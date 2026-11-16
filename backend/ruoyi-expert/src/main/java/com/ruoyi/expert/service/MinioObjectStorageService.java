package com.ruoyi.expert.service;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.config.ExpertStorageProperties;

/**
 * 使用 S3 Path-Style API 和 AWS Signature V4 访问 MinIO。
 *
 * 不依赖 MinIO SDK，避免将文件访问绑定到单一客户端版本。
 */
@Service
public class MinioObjectStorageService implements ExpertObjectStorage
{
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Autowired
    private ExpertStorageProperties properties;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public boolean isConfigured()
    {
        return properties.isMinioConfigured();
    }

    @Override
    public String upload(Long userId, String originalFilename, String contentType, long size, InputStream input)
    {
        requireConfigured();
        try
        {
            byte[] bytes = input.readAllBytes();
            ensureBucket();
            String safeName = sanitizeFilename(originalFilename);
            String objectKey = "experts/" + userId + "/" + ZonedDateTime.now().format(DATE_STAMP)
                    + "/" + UUID.randomUUID() + "-" + safeName;
            HttpResponse<String> response = signedRequest("PUT", objectKey, bytes,
                    contentType == null ? "application/octet-stream" : contentType);
            if (response.statusCode() / 100 != 2)
            {
                throw storageError("MinIO上传失败", response);
            }
            return "minio://" + properties.getBucket() + "/" + objectKey;
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("MinIO上传失败：" + ex.getMessage());
        }
    }

    @Override
    public byte[] download(String objectUri)
    {
        requireConfigured();
        try
        {
            String objectKey = parseObjectKey(objectUri);
            HttpResponse<byte[]> response = signedByteRequest("GET", objectKey, new byte[0], null);
            if (response.statusCode() / 100 != 2)
            {
                throw new ServiceException("MinIO读取失败，状态码：" + response.statusCode());
            }
            return response.body();
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("MinIO读取失败：" + ex.getMessage());
        }
    }

    @Override
    public void delete(String objectUri)
    {
        requireConfigured();
        try
        {
            String objectKey = parseObjectKey(objectUri);
            HttpResponse<String> response = signedRequest("DELETE", objectKey, new byte[0], null);
            if (response.statusCode() / 100 != 2 && response.statusCode() != 404)
            {
                throw storageError("MinIO删除失败", response);
            }
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("MinIO删除失败：" + ex.getMessage());
        }
    }

    private void ensureBucket()
    {
        try
        {
            HttpResponse<String> head = bucketRequest("HEAD");
            if (head.statusCode() / 100 == 2)
            {
                return;
            }
            HttpResponse<String> create = bucketRequest("PUT");
            if (create.statusCode() / 100 != 2 && create.statusCode() != 409)
            {
                throw storageError("MinIO Bucket创建失败", create);
            }
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("MinIO连接失败：" + ex.getMessage());
        }
    }

    private HttpResponse<String> bucketRequest(String method) throws Exception
    {
        String bucket = properties.getBucket();
        String canonicalUri = "/" + encodeSegment(bucket);
        HttpResponse<String> response = execute(method, canonicalUri, new byte[0], null,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response;
    }

    private HttpResponse<String> signedRequest(String method, String objectKey, byte[] body, String contentType)
            throws Exception
    {
        String canonicalUri = "/" + encodeSegment(properties.getBucket()) + "/" + encodeObjectKey(objectKey);
        return execute(method, canonicalUri, body, contentType,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> signedByteRequest(String method, String objectKey, byte[] body,
            String contentType) throws Exception
    {
        String canonicalUri = "/" + encodeSegment(properties.getBucket()) + "/" + encodeObjectKey(objectKey);
        return execute(method, canonicalUri, body, contentType,
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private <T> HttpResponse<T> execute(String method, String canonicalUri, byte[] body, String contentType,
            HttpResponse.BodyHandler<T> bodyHandler) throws Exception
    {
        URI endpoint = URI.create(properties.getEndpoint());
        URI target = URI.create(properties.getEndpoint().replaceAll("/+$", "") + canonicalUri);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(AMZ_DATE);
        String dateStamp = now.format(DATE_STAMP);
        String payloadHash = sha256Hex(body);
        String host = endpoint.getAuthority();
        String canonicalHeaders = "host:" + host + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = method + "\n" + canonicalUri + "\n\n" + canonicalHeaders + "\n"
                + signedHeaders + "\n" + payloadHash;
        String scope = dateStamp + "/" + properties.getRegion() + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String signature = HexFormat.of().formatHex(hmac(signingKey(dateStamp), stringToSign));
        String authorization = "AWS4-HMAC-SHA256 Credential=" + properties.getAccessKey() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(60))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authorization);
        if (contentType != null)
        {
            builder.header("Content-Type", contentType);
        }
        if ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method))
        {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        else
        {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }
        return client.send(builder.build(), bodyHandler);
    }

    private byte[] signingKey(String dateStamp)
    {
        byte[] dateKey = hmac(("AWS4" + properties.getSecretKey()).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] regionKey = hmac(dateKey, properties.getRegion());
        byte[] serviceKey = hmac(regionKey, "s3");
        return hmac(serviceKey, "aws4_request");
    }

    private static byte[] hmac(byte[] key, String value)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("HMAC签名失败", ex);
        }
    }

    private static String sha256Hex(byte[] value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("SHA-256计算失败", ex);
        }
    }

    private String parseObjectKey(String objectUri)
    {
        String prefix = "minio://" + properties.getBucket() + "/";
        if (objectUri == null || !objectUri.startsWith(prefix))
        {
            throw new ServiceException("无效的MinIO对象地址");
        }
        return objectUri.substring(prefix.length());
    }

    private String encodeObjectKey(String objectKey)
    {
        String[] segments = objectKey.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String segment : segments)
        {
            if (encoded.length() > 0)
            {
                encoded.append('/');
            }
            encoded.append(encodeSegment(segment));
        }
        return encoded.toString();
    }

    private static String encodeSegment(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%7E", "~");
    }

    private static String sanitizeFilename(String filename)
    {
        if (filename == null || filename.isBlank())
        {
            return "material.bin";
        }
        String normalized = filename.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        String safe = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.length() > 120 ? safe.substring(safe.length() - 120) : safe;
    }

    private void requireConfigured()
    {
        if (!properties.isMinioConfigured())
        {
            throw new ServiceException("MinIO尚未配置，请设置 MINIO_ENDPOINT、MINIO_ACCESS_KEY、MINIO_SECRET_KEY 和 MINIO_BUCKET");
        }
    }

    private static ServiceException storageError(String prefix, HttpResponse<String> response)
    {
        String detail = response.body() == null ? "" : response.body();
        if (detail.length() > 500)
        {
            detail = detail.substring(0, 500);
        }
        return new ServiceException(prefix + "，状态码：" + response.statusCode() + " " + detail);
    }
}
