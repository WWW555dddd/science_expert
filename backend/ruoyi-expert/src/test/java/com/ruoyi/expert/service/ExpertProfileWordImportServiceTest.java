package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertProfile;

class ExpertProfileWordImportServiceTest
{
    private final ExpertProfileWordImportService service = new ExpertProfileWordImportService();

    @Test
    void importsFilledTemplateIntoProfileFields() throws Exception
    {
        byte[] template = service.createTemplate();
        Path templateOutput = Path.of("target", "test-output", "专家信息导入模板.docx");
        Files.createDirectories(templateOutput.getParent());
        Files.write(templateOutput, template);
        byte[] filled;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(template));
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            XWPFTable table = findProfileTable(document);
            fill(table, "专家姓名 *", "张专家");
            fill(table, "所属单位", "江西省科学院");
            fill(table, "职称职务", "研究员");
            fill(table, "服务区域", "江西省南昌市");
            fill(table, "专业方向", "新材料");
            fill(table, "产业领域", "新能源");
            fill(table, "联系电话", "13812345678");
            fill(table, "电子邮箱", "zhang@example.com");
            XWPFTable summaryTable = findTableContaining(document, "专家简介");
            fillMultiline(summaryTable, "专家简介",
                    List.of("长期从事新能源材料研究。", "承担多项重点项目。"));
            document.write(output);
            filled = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "专家信息.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", filled);

        Map<String, Object> result = service.importProfile(file);
        ExpertProfile profile = (ExpertProfile) result.get("profile");

        assertEquals("张专家", profile.getRealName());
        assertEquals("江西省科学院", profile.getOrganization());
        assertEquals("研究员", profile.getProfessionalTitle());
        assertEquals("江西省南昌市", profile.getServiceRegion());
        assertEquals("新材料", profile.getResearchField());
        assertEquals("新能源", profile.getIndustryField());
        assertEquals("13812345678", profile.getPhone());
        assertEquals("zhang@example.com", profile.getEmail());
        assertEquals("长期从事新能源材料研究。\n承担多项重点项目。", profile.getCareerSummary());
        assertEquals(9, ((List<?>) result.get("recognizedFields")).size());
    }

    @Test
    void importsAliasLabelsFromParagraphs() throws Exception
    {
        byte[] content;
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            document.createParagraph().createRun().setText("姓名：李专家");
            document.createParagraph().createRun().setText("工作单位：某研究院");
            document.createParagraph().createRun().setText("邮箱：li@example.com");
            document.write(output);
            content = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "专家信息.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);

        ExpertProfile profile = (ExpertProfile) service.importProfile(file).get("profile");

        assertEquals("李专家", profile.getRealName());
        assertEquals("某研究院", profile.getOrganization());
        assertEquals("li@example.com", profile.getEmail());
    }

    @Test
    void rejectsEmptyTemplateAndNonDocxFiles()
    {
        MockMultipartFile emptyTemplate = new MockMultipartFile("file", "专家模板.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                service.createTemplate());
        MockMultipartFile oldWord = new MockMultipartFile("file", "专家模板.doc",
                "application/msword", new byte[] { 1, 2, 3 });

        assertThrows(ServiceException.class, () -> service.importProfile(emptyTemplate));
        assertThrows(ServiceException.class, () -> service.importProfile(oldWord));
    }

    private static void fill(XWPFTable table, String label, String value)
    {
        for (XWPFTableRow row : table.getRows())
        {
            if (label.equals(row.getCell(0).getText().trim()))
            {
                row.getCell(1).setText(value);
                return;
            }
        }
        throw new IllegalStateException("模板缺少字段：" + label);
    }

    private static XWPFTable findProfileTable(XWPFDocument document)
    {
        for (XWPFTable table : document.getTables())
        {
            for (XWPFTableRow row : table.getRows())
            {
                if (row.getTableCells().size() >= 2
                        && "专家姓名 *".equals(row.getCell(0).getText().trim()))
                {
                    return table;
                }
            }
        }
        throw new IllegalStateException("模板缺少专家基本信息表");
    }

    private static XWPFTable findTableContaining(XWPFDocument document, String label)
    {
        for (XWPFTable table : document.getTables())
        {
            for (XWPFTableRow row : table.getRows())
            {
                if (row.getTableCells().size() >= 2
                        && label.equals(row.getCell(0).getText().trim()))
                {
                    return table;
                }
            }
        }
        throw new IllegalStateException("模板缺少表格字段：" + label);
    }

    private static void fillMultiline(XWPFTable table, String label, List<String> values)
    {
        for (XWPFTableRow row : table.getRows())
        {
            if (label.equals(row.getCell(0).getText().trim()))
            {
                row.getCell(1).removeParagraph(0);
                for (String value : values)
                {
                    row.getCell(1).addParagraph().createRun().setText(value);
                }
                return;
            }
        }
        throw new IllegalStateException("模板缺少字段：" + label);
    }
}
