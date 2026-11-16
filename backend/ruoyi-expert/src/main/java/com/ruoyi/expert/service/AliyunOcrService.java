package com.ruoyi.expert.service;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.SignatureVersion;
import com.aliyuncs.auth.signers.SignatureAlgorithm;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.config.ExpertOcrProperties;

/**
 * 阿里云 OCR 统一识别接口 RecognizeAllText 适配器。
 */
@Service
public class AliyunOcrService
{
    private static final Logger log = LoggerFactory.getLogger(AliyunOcrService.class);
    private static final String OCR_API_VERSION = "2021-07-07";

    @Autowired
    private ExpertOcrProperties properties;

    private volatile DefaultAcsClient client;

    public boolean isAvailable()
    {
        return properties.isEnabled() && properties.isAliyunConfigured();
    }

    public String recognize(byte[] fileBytes, String extension, String materialCategory)
    {
        if (!isAvailable())
        {
            log.warn("阿里云OCR未配置，请设置 ALIYUN_OCR_ACCESS_KEY_ID 和 ALIYUN_OCR_ACCESS_KEY_SECRET");
            return "";
        }
        if (fileBytes == null || fileBytes.length == 0)
        {
            return "";
        }
        if (fileBytes.length > 10 * 1024 * 1024)
        {
            log.warn("阿里云OCR文件超过10MB限制");
            return "";
        }
        try
        {
            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysProtocol(ProtocolType.HTTPS);
            request.setSysDomain(properties.getEndpoint());
            request.setSysUriPattern("/");
            request.setSysVersion(OCR_API_VERSION);
            request.setSysAction("RecognizeAllText");
            request.setSysSignatureVersion(SignatureVersion.V3);
            request.setSysSignatureAlgorithm(SignatureAlgorithm.ACS3_HMAC_SHA256);
            request.setSysConnectTimeout(15000);
            request.setSysReadTimeout(properties.getTimeoutSeconds() * 1000);
            request.putQueryParameter("Type", resolveType(materialCategory));
            request.setSysAccept(FormatType.JSON);
            request.setHttpContent(fileBytes, contentType(extension), FormatType.RAW);

            CommonResponse response = getClient().getCommonResponse(request);
            return parseText(response);
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("阿里云OCR调用失败：" + ex.getMessage());
        }
    }

    private DefaultAcsClient getClient()
    {
        if (client == null)
        {
            synchronized (this)
            {
                if (client == null)
                {
                    DefaultProfile profile = DefaultProfile.getProfile(properties.getRegion(),
                            properties.getAccessKey(), properties.getSecretKey());
                    client = new DefaultAcsClient(profile);
                }
            }
        }
        return client;
    }

    private String parseText(CommonResponse response)
    {
        String body = response.getData();
        if (response.getHttpStatus() / 100 != 2)
        {
            throw new ServiceException("阿里云OCR请求失败，HTTP状态码：" + response.getHttpStatus()
                    + " " + abbreviate(body, 500));
        }
        JSONObject root = JSON.parseObject(body);
        String code = root.getString("Code");
        if (code != null && !code.startsWith("2") && !"OK".equalsIgnoreCase(code))
        {
            throw new ServiceException("阿里云OCR识别失败：" + code + " " + root.getString("Message"));
        }
        JSONObject data = root.getJSONObject("Data");
        if (data == null)
        {
            return "";
        }
        StringBuilder text = new StringBuilder();
        append(text, data.getString("Content"));
        JSONArray subImages = data.getJSONArray("SubImages");
        if (subImages != null)
        {
            for (int i = 0; i < subImages.size(); i++)
            {
                JSONObject image = subImages.getJSONObject(i);
                appendKv(text, image.getJSONObject("KvInfo"));
                appendBlocks(text, image.getJSONObject("BlockInfo"));
                appendTables(text, image.getJSONObject("TableInfo"));
            }
        }
        return text.toString().trim();
    }

    private static void appendKv(StringBuilder text, JSONObject kvInfo)
    {
        if (kvInfo == null)
        {
            return;
        }
        String data = kvInfo.getString("Data");
        if (data == null || data.isBlank())
        {
            return;
        }
        try
        {
            JSONObject kv = JSON.parseObject(data);
            for (String key : kv.keySet())
            {
                append(text, key + "：" + kv.getString(key));
            }
        }
        catch (Exception ex)
        {
            append(text, data);
        }
    }

    private static void appendBlocks(StringBuilder text, JSONObject blockInfo)
    {
        if (blockInfo == null)
        {
            return;
        }
        JSONArray details = blockInfo.getJSONArray("BlockDetails");
        if (details != null)
        {
            for (int i = 0; i < details.size(); i++)
            {
                append(text, details.getJSONObject(i).getString("BlockContent"));
            }
        }
    }

    private static void appendTables(StringBuilder text, JSONObject tableInfo)
    {
        if (tableInfo == null)
        {
            return;
        }
        JSONArray tables = tableInfo.getJSONArray("TableDetails");
        if (tables == null)
        {
            return;
        }
        for (int i = 0; i < tables.size(); i++)
        {
            JSONArray cells = tables.getJSONObject(i).getJSONArray("CellDetails");
            if (cells == null)
            {
                continue;
            }
            for (int j = 0; j < cells.size(); j++)
            {
                append(text, cells.getJSONObject(j).getString("CellContent"));
            }
        }
    }

    private String resolveType(String materialCategory)
    {
        if ("IDENTITY".equalsIgnoreCase(materialCategory))
        {
            return "IdCard";
        }
        return properties.getDefaultType() == null || properties.getDefaultType().isBlank()
                ? "Advanced" : properties.getDefaultType();
    }

    private static String contentType(String extension)
    {
        String value = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return switch (value)
        {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "bmp" -> "image/bmp";
            case "gif" -> "image/gif";
            case "tif", "tiff" -> "image/tiff";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private static void append(StringBuilder text, String value)
    {
        if (value != null && !value.isBlank())
        {
            if (text.length() > 0)
            {
                text.append('\n');
            }
            text.append(value.trim());
        }
    }

    private static String abbreviate(String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
