package com.ruoyi.expert.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertMaterialFile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 受权限控制的专家原始材料预览。
 */
@Service
public class ExpertMaterialPreviewService
{
    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertObjectStorage objectStorage;

    public PreviewFile preview(Long materialId, Long ownerUserId, boolean checkOwner, boolean textMode)
    {
        ExpertMaterial material = platformMapper.selectMaterialById(materialId);
        if (material == null)
        {
            throw new ServiceException("资料文件不存在");
        }
        if (checkOwner && !material.getUserId().equals(ownerUserId))
        {
            throw new ServiceException("无权预览该资料文件");
        }
        List<ExpertMaterialFile> files = platformMapper.selectMaterialFiles(materialId);
        if (!files.isEmpty())
        {
            ExpertMaterialFile file = files.get(0);
            return preview(file.getFileName(), file.getFilePath(), file.getFileType(), textMode);
        }
        return preview(material.getFileName(), material.getFilePath(), material.getFileType(), textMode);
    }

    public PreviewFile previewFile(Long materialId, Long fileId, Long ownerUserId,
            boolean checkOwner, boolean textMode)
    {
        ExpertMaterial material = platformMapper.selectMaterialById(materialId);
        if (material == null)
        {
            throw new ServiceException("资料版本不存在");
        }
        if (checkOwner && !material.getUserId().equals(ownerUserId))
        {
            throw new ServiceException("无权预览该资料文件");
        }
        ExpertMaterialFile file = platformMapper.selectMaterialFile(materialId, fileId);
        if (file == null)
        {
            throw new ServiceException("版本文件不存在");
        }
        return preview(file.getFileName(), file.getFilePath(), file.getFileType(), textMode);
    }

    private PreviewFile preview(String fileName, String filePath, String fileType,
            boolean textMode)
    {
        byte[] bytes = loadBytes(filePath);
        String type = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        if (textMode)
        {
            if ("docx".equals(type))
            {
                bytes = extractDocxText(bytes).getBytes(StandardCharsets.UTF_8);
            }
            else if ("txt".equals(type) || "md".equals(type) || "csv".equals(type))
            {
                // 文本文件直接返回原内容。
            }
            else
            {
                throw new ServiceException("该文件类型不支持文本预览");
            }
            return new PreviewFile(fileName, "text/plain;charset=UTF-8", bytes);
        }
        return new PreviewFile(fileName, contentType(type), bytes);
    }

    private byte[] loadBytes(String filePath)
    {
        try
        {
            if (filePath != null && filePath.startsWith("minio://"))
            {
                return objectStorage.download(filePath);
            }
            String relative = filePath;
            if (relative != null && relative.startsWith("/profile"))
            {
                relative = relative.substring("/profile".length());
            }
            Path root = Path.of(RuoYiConfig.getProfile()).toAbsolutePath().normalize();
            Path file = root.resolve(relative == null ? "" : relative.replaceFirst("^[/\\\\]+", "")).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file))
            {
                throw new ServiceException("资料文件不存在或路径非法");
            }
            return Files.readAllBytes(file);
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("读取资料文件失败：" + ex.getMessage());
        }
    }

    private String extractDocxText(byte[] bytes)
    {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes)))
        {
            for (XWPFParagraph paragraph : document.getParagraphs())
            {
                text.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables())
            {
                for (XWPFTableRow row : table.getRows())
                {
                    for (XWPFTableCell cell : row.getTableCells())
                    {
                        text.append(cell.getText()).append('\t');
                    }
                    text.append('\n');
                }
            }
            return text.toString();
        }
        catch (Exception ex)
        {
            throw new ServiceException("Word文档预览失败：" + ex.getMessage());
        }
    }

    private static String contentType(String type)
    {
        return switch (type)
        {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "txt", "md", "csv" -> "text/plain;charset=UTF-8";
            default -> "application/octet-stream";
        };
    }

    public static class PreviewFile
    {
        private final String fileName;
        private final String contentType;
        private final byte[] bytes;

        public PreviewFile(String fileName, String contentType, byte[] bytes)
        {
            this.fileName = fileName;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        public String getFileName()
        {
            return fileName;
        }

        public String getContentType()
        {
            return contentType;
        }

        public byte[] getBytes()
        {
            return bytes;
        }
    }
}
