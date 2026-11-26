package com.ruoyi.expert.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertProfile;

/**
 * 专家基本档案 Word 模板生成与导入。
 */
@Service
public class ExpertProfileWordImportService
{
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String TITLE_FONT = "Microsoft YaHei";
    private static final String BODY_FONT = "宋体";
    private static final String TEXT_COLOR = "000000";
    private static final String MUTED_COLOR = "595959";
    private static final String HEADER_FILL = "1F4E79";
    private static final String SECTION_FILL = "D9EAF7";
    private static final String ALTERNATE_FILL = "F7F9FB";
    private static final String BORDER_COLOR = "D9D9D9";
    private static final DateTimeFormatter TEMPLATE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Map<String, String> TEMPLATE_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> LABEL_FIELDS = new LinkedHashMap<>();

    static
    {
        register("专家姓名", "realName", "姓名");
        register("所属单位", "organization", "工作单位", "所在单位", "单位");
        register("职称职务", "professionalTitle", "职称", "职务");
        register("服务区域", "serviceRegion", "服务地区", "服务范围");
        register("专业方向", "researchField", "研究方向", "专业领域");
        register("产业领域", "industryField", "产业方向", "行业领域");
        register("联系电话", "phone", "电话", "手机", "手机号码");
        register("电子邮箱", "email", "邮箱", "电子邮件", "Email", "E-mail");
        register("专家简介", "careerSummary", "个人简介", "专家介绍");
    }

    public byte[] createTemplate()
    {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            configurePage(document);
            addFooter(document);
            addCoverPage(document);
            addPageBreak(document);
            addInstructions(document);
            addProfileTable(document);
            addPageBreak(document);
            addSummaryTable(document);
            addSubmissionChecklist(document);
            addImportSteps(document);
            addDeclaration(document);

            document.write(output);
            return output.toByteArray();
        }
        catch (Exception ex)
        {
            throw new ServiceException("生成专家信息Word模板失败：" + ex.getMessage());
        }
    }

    public Map<String, Object> importProfile(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择需要导入的Word模板");
        }
        if (file.getSize() > MAX_FILE_SIZE)
        {
            throw new ServiceException("Word模板不能超过5MB");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".docx"))
        {
            throw new ServiceException("仅支持.docx格式的Word模板");
        }

        ExpertProfile profile = new ExpertProfile();
        Set<String> recognizedFields = new LinkedHashSet<>();
        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(file.getBytes())))
        {
            for (XWPFTable table : document.getTables())
            {
                parseTable(table, profile, recognizedFields);
            }
            for (XWPFParagraph paragraph : document.getParagraphs())
            {
                parseLine(paragraph.getText(), profile, recognizedFields);
            }
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("Word模板解析失败，请确认文件为有效的.docx文档");
        }
        if (recognizedFields.isEmpty())
        {
            throw new ServiceException("未从Word模板中识别到专家信息，请使用系统下载的模板填写");
        }
        validate(profile);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("recognizedFields", List.copyOf(recognizedFields));
        return result;
    }

    private static void register(String templateLabel, String field, String... aliases)
    {
        TEMPLATE_LABELS.put(templateLabel, field);
        LABEL_FIELDS.put(normalizeLabel(templateLabel), field);
        for (String alias : aliases)
        {
            LABEL_FIELDS.put(normalizeLabel(alias), field);
        }
    }

    private static void configurePage(XWPFDocument document)
    {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.isSetPgSz()
                ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(12240));
        pageSize.setH(BigInteger.valueOf(15840));
        pageSize.setOrient(STPageOrientation.PORTRAIT);

        CTPageMar margin = section.isSetPgMar()
                ? section.getPgMar() : section.addNewPgMar();
        margin.setTop(BigInteger.valueOf(1080));
        margin.setBottom(BigInteger.valueOf(1080));
        margin.setLeft(BigInteger.valueOf(1080));
        margin.setRight(BigInteger.valueOf(1080));
        margin.setHeader(BigInteger.valueOf(540));
        margin.setFooter(BigInteger.valueOf(540));
    }

    private static void addFooter(XWPFDocument document)
    {
        XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph paragraph = footer.getParagraphArray(0);
        if (paragraph == null)
        {
            paragraph = footer.createParagraph();
        }
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        XWPFRun run = paragraph.createRun();
        styleRun(run, BODY_FONT, 9, false, MUTED_COLOR);
        run.setText("科技保险专家管理平台  |  专家基本信息导入模板  |  仅用于专家档案登记");
    }

    private static void addCoverPage(XWPFDocument document)
    {
        addSpacer(document, 3);

        XWPFParagraph eyebrow = document.createParagraph();
        eyebrow.setAlignment(ParagraphAlignment.CENTER);
        eyebrow.setSpacingAfter(18);
        XWPFRun eyebrowRun = eyebrow.createRun();
        styleRun(eyebrowRun, TITLE_FONT, 10.5, false, MUTED_COLOR);
        eyebrowRun.setText("TECHNOLOGY INSURANCE EXPERT PLATFORM");

        XWPFParagraph title = document.createParagraph();
        title.setStyle("Title");
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(10);
        XWPFRun titleRun = title.createRun();
        styleRun(titleRun, TITLE_FONT, 26, true, TEXT_COLOR);
        titleRun.setText("科技保险专家信息登记表");

        XWPFParagraph subtitle = document.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        subtitle.setSpacingAfter(42);
        XWPFRun subtitleRun = subtitle.createRun();
        styleRun(subtitleRun, TITLE_FONT, 14, false, TEXT_COLOR);
        subtitleRun.setText("Word 信息导入模板");

        XWPFTable metadata = document.createTable(4, 2);
        metadata.setWidth("72%");
        metadata.setTableAlignment(TableRowAlign.CENTER);
        formatTable(metadata, 2100, 5000);
        addMetadataRow(metadata.getRow(0), "模板名称", "专家基本档案导入模板", true);
        addMetadataRow(metadata.getRow(1), "模板版本", "V2.0", false);
        addMetadataRow(metadata.getRow(2), "适用系统", "科技保险专家管理与协同服务平台", false);
        addMetadataRow(metadata.getRow(3), "更新日期",
                LocalDate.now().format(TEMPLATE_DATE_FORMAT), false);

        addSpacer(document, 2);
        XWPFParagraph purpose = document.createParagraph();
        purpose.setAlignment(ParagraphAlignment.CENTER);
        purpose.setSpacingAfter(8);
        XWPFRun purposeRun = purpose.createRun();
        styleRun(purposeRun, BODY_FONT, 10.5, false, TEXT_COLOR);
        purposeRun.setText("用于批量维护专家基本档案信息。请依照本模板填写，并在保存后导入系统。");

        XWPFParagraph note = document.createParagraph();
        note.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun noteRun = note.createRun();
        styleRun(noteRun, BODY_FONT, 9.5, false, MUTED_COLOR);
        noteRun.setText("文件格式：.docx    建议使用 Microsoft Word 或兼容办公软件编辑");
    }

    private static void addInstructions(XWPFDocument document)
    {
        addSectionHeading(document, "填写说明");
        addNumberedInstruction(document, "1.", "仅填写“填写内容”列，请勿修改字段名称、表格结构或模板版本。");
        addNumberedInstruction(document, "2.", "专家姓名为必填项，其余信息请依据当前实际情况完整填写。");
        addNumberedInstruction(document, "3.", "“专家简介”支持多行文本，建议填写主要履历、专业能力和代表性经历。");
        addNumberedInstruction(document, "4.", "未填写字段不会覆盖系统中已有信息；导入前请核对所有内容。");
        addNumberedInstruction(document, "5.", "完成填写后请保持 .docx 格式，并在专家档案编辑页面导入。");
        addSectionHeading(document, "专家基本信息");
    }

    private static void addProfileTable(XWPFDocument document)
    {
        int rows = 1 + 2 + 8;
        XWPFTable table = document.createTable(rows, 2);
        table.setWidth("100%");
        formatTable(table, 2300, 7780);

        XWPFTableRow header = table.getRow(0);
        header.setHeight(480);
        header.setHeightRule(TableRowHeightRule.AT_LEAST);
        setCell(header.getCell(0), "字段项目", true, 10.5, HEADER_FILL, "FFFFFF",
                ParagraphAlignment.CENTER);
        setCell(header.getCell(1), "填写内容", true, 10.5, HEADER_FILL, "FFFFFF",
                ParagraphAlignment.LEFT);

        int rowIndex = 1;
        rowIndex = addSectionRow(table, rowIndex, "一、身份与联系信息");
        rowIndex = addFieldRow(table, rowIndex, "专家姓名 *", 480, false);
        rowIndex = addFieldRow(table, rowIndex, "所属单位", 480, true);
        rowIndex = addFieldRow(table, rowIndex, "职称职务", 480, false);
        rowIndex = addFieldRow(table, rowIndex, "服务区域", 480, true);

        rowIndex = addSectionRow(table, rowIndex, "二、专业与服务信息");
        rowIndex = addFieldRow(table, rowIndex, "专业方向", 480, false);
        rowIndex = addFieldRow(table, rowIndex, "产业领域", 480, true);
        rowIndex = addFieldRow(table, rowIndex, "联系电话", 480, false);
        addFieldRow(table, rowIndex, "电子邮箱", 480, true);
    }

    private static void addSummaryTable(XWPFDocument document)
    {
        addSectionHeading(document, "专家简介");
        XWPFTable table = document.createTable(3, 2);
        table.setWidth("100%");
        formatTable(table, 2300, 7780);

        XWPFTableRow header = table.getRow(0);
        header.setHeight(480);
        header.setHeightRule(TableRowHeightRule.AT_LEAST);
        setCell(header.getCell(0), "字段项目", true, 10.5, HEADER_FILL, "FFFFFF",
                ParagraphAlignment.CENTER);
        setCell(header.getCell(1), "填写内容", true, 10.5, HEADER_FILL, "FFFFFF",
                ParagraphAlignment.LEFT);
        addSectionRow(table, 1, "三、履历概述");
        addFieldRow(table, 2, "专家简介", 2400, false);
    }

    private static void addSubmissionChecklist(XWPFDocument document)
    {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(220);
        heading.setSpacingAfter(80);
        XWPFRun headingRun = heading.createRun();
        styleRun(headingRun, TITLE_FONT, 12, true, TEXT_COLOR);
        headingRun.setText("提交前核对");
        addChecklistItem(document, "专家姓名、所属单位、职称职务已填写完整");
        addChecklistItem(document, "联系电话和电子邮箱准确可用");
        addChecklistItem(document, "专业方向、产业领域与服务区域已按实际情况填写");
        addChecklistItem(document, "专家简介已补充充分，且不存在无关或空白内容");
    }

    private static void addChecklistItem(XWPFDocument document, String text)
    {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(180);
        paragraph.setSpacingAfter(55);
        XWPFRun run = paragraph.createRun();
        styleRun(run, BODY_FONT, 10, false, TEXT_COLOR);
        run.setText("□  " + text);
    }

    private static void addImportSteps(XWPFDocument document)
    {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(180);
        heading.setSpacingAfter(70);
        XWPFRun headingRun = heading.createRun();
        styleRun(headingRun, TITLE_FONT, 12, true, TEXT_COLOR);
        headingRun.setText("导入步骤");
        addNumberedInstruction(document, "1.", "确认内容后保存当前文档，并保持 .docx 文件格式。");
        addNumberedInstruction(document, "2.", "进入专家档案编辑页面，选择“导入Word模板”并上传文件。");
        addNumberedInstruction(document, "3.", "系统完成字段回填后，请逐项核对并保存专家档案。");
    }

    private static void addDeclaration(XWPFDocument document)
    {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(220);
        heading.setSpacingAfter(90);
        XWPFRun headingRun = heading.createRun();
        styleRun(headingRun, TITLE_FONT, 12, true, TEXT_COLOR);
        headingRun.setText("填写确认");

        XWPFParagraph confirmation = document.createParagraph();
        confirmation.setSpacingAfter(80);
        XWPFRun confirmationRun = confirmation.createRun();
        styleRun(confirmationRun, BODY_FONT, 10, false, TEXT_COLOR);
        confirmationRun.setText("□ 本人已核对所填信息，确认内容真实、准确、完整。");

        XWPFParagraph submission = document.createParagraph();
        XWPFRun submissionRun = submission.createRun();
        styleRun(submissionRun, BODY_FONT, 10, false, MUTED_COLOR);
        submissionRun.setText("填写人（签字）：________________    日期：______年____月____日");
    }

    private static void addSectionHeading(XWPFDocument document, String text)
    {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading1");
        paragraph.setSpacingBefore(120);
        paragraph.setSpacingAfter(100);
        XWPFRun run = paragraph.createRun();
        styleRun(run, TITLE_FONT, 15, true, TEXT_COLOR);
        run.setText(text);
    }

    private static void addNumberedInstruction(XWPFDocument document, String number, String text)
    {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(180);
        paragraph.setSpacingAfter(70);
        XWPFRun numberRun = paragraph.createRun();
        styleRun(numberRun, TITLE_FONT, 10, true, TEXT_COLOR);
        numberRun.setText(number + "  ");
        XWPFRun textRun = paragraph.createRun();
        styleRun(textRun, BODY_FONT, 10.5, false, TEXT_COLOR);
        textRun.setText(text);
    }

    private static int addSectionRow(XWPFTable table, int rowIndex, String text)
    {
        XWPFTableRow row = table.getRow(rowIndex);
        row.setHeight(420);
        row.setHeightRule(TableRowHeightRule.AT_LEAST);
        setCell(row.getCell(0), text, true, 10.5, SECTION_FILL, TEXT_COLOR,
                ParagraphAlignment.LEFT);
        setCell(row.getCell(1), "", false, 10.5, SECTION_FILL, TEXT_COLOR,
                ParagraphAlignment.LEFT);
        return rowIndex + 1;
    }

    private static int addFieldRow(XWPFTable table, int rowIndex, String label,
            int height, boolean alternate)
    {
        XWPFTableRow row = table.getRow(rowIndex);
        row.setHeight(height);
        row.setHeightRule(TableRowHeightRule.AT_LEAST);
        String fill = alternate ? ALTERNATE_FILL : null;
        setCell(row.getCell(0), label, true, 10, fill, TEXT_COLOR,
                ParagraphAlignment.LEFT);
        setCell(row.getCell(1), "", false, 10, fill, TEXT_COLOR,
                ParagraphAlignment.LEFT);
        return rowIndex + 1;
    }

    private static void addMetadataRow(XWPFTableRow row, String label, String value,
            boolean header)
    {
        row.setHeight(520);
        row.setHeightRule(TableRowHeightRule.AT_LEAST);
        String fill = header ? HEADER_FILL : ALTERNATE_FILL;
        String color = header ? "FFFFFF" : TEXT_COLOR;
        setCell(row.getCell(0), label, true, 10, fill, color, ParagraphAlignment.LEFT);
        setCell(row.getCell(1), value, false, 10, header ? fill : null, color,
                ParagraphAlignment.LEFT);
    }

    private static void setCell(XWPFTableCell cell, String text, boolean bold,
            double fontSize, String fill, String color, ParagraphAlignment alignment)
    {
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        if (fill != null)
        {
            CTTcPr properties = cell.getCTTc().isSetTcPr()
                    ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
            CTShd shading = properties.isSetShd() ? properties.getShd() : properties.addNewShd();
            shading.setVal(STShd.CLEAR);
            shading.setColor("auto");
            shading.setFill(fill);
        }
        while (cell.getParagraphs().size() > 1)
        {
            cell.removeParagraph(1);
        }
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        paragraph.setAlignment(alignment);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        for (int index = paragraph.getRuns().size() - 1; index >= 0; index--)
        {
            paragraph.removeRun(index);
        }
        if (!text.isEmpty())
        {
            XWPFRun run = paragraph.createRun();
            styleRun(run, bold ? TITLE_FONT : BODY_FONT, fontSize, bold, color);
            run.setText(text);
        }
    }

    private static void formatTable(XWPFTable table, int labelWidth, int valueWidth)
    {
        table.setCellMargins(120, 140, 120, 140);
        CTTblPr properties = table.getCTTbl().getTblPr();
        CTTblWidth width = properties.isSetTblW()
                ? properties.getTblW() : properties.addNewTblW();
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(labelWidth + valueWidth));

        CTTblBorders borders = properties.isSetTblBorders()
                ? properties.getTblBorders() : properties.addNewTblBorders();
        setBorder(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        setBorder(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        setBorder(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        setBorder(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
        setBorder(borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH());
        setBorder(borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV());

        for (XWPFTableRow row : table.getRows())
        {
            if (row.getTableCells().size() >= 2)
            {
                setCellWidth(row.getCell(0), labelWidth);
                setCellWidth(row.getCell(1), valueWidth);
            }
        }
    }

    private static void setBorder(CTBorder border)
    {
        border.setVal(STBorder.SINGLE);
        border.setColor(BORDER_COLOR);
        border.setSz(BigInteger.valueOf(4));
        border.setSpace(BigInteger.ZERO);
    }

    private static void setCellWidth(XWPFTableCell cell, int width)
    {
        CTTcPr properties = cell.getCTTc().isSetTcPr()
                ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth cellWidth = properties.isSetTcW()
                ? properties.getTcW() : properties.addNewTcW();
        cellWidth.setType(STTblWidth.DXA);
        cellWidth.setW(BigInteger.valueOf(width));
    }

    private static void styleRun(XWPFRun run, String font, double fontSize,
            boolean bold, String color)
    {
        run.setFontFamily(font, XWPFRun.FontCharRange.ascii);
        run.setFontFamily(font, XWPFRun.FontCharRange.hAnsi);
        run.setFontFamily(font, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setColor(color);
    }

    private static void addSpacer(XWPFDocument document, int count)
    {
        for (int index = 0; index < count; index++)
        {
            document.createParagraph();
        }
    }

    private static void addPageBreak(XWPFDocument document)
    {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
    }

    private static void parseTable(XWPFTable table, ExpertProfile profile,
            Set<String> recognizedFields)
    {
        for (XWPFTableRow row : table.getRows())
        {
            List<XWPFTableCell> cells = row.getTableCells();
            if (cells.size() >= 2)
            {
                String label = normalizeLabel(readCellText(cells.get(0)));
                boolean longText = "careerSummary".equals(LABEL_FIELDS.get(label));
                applyField(label, normalizeValue(readCellText(cells.get(1)), longText),
                        profile, recognizedFields);
            }
            else
            {
                for (XWPFTableCell cell : cells)
                {
                    parseLine(readCellText(cell), profile, recognizedFields);
                }
            }
        }
    }

    private static String readCellText(XWPFTableCell cell)
    {
        return cell.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static void parseLine(String text, ExpertProfile profile,
            Set<String> recognizedFields)
    {
        if (text == null)
        {
            return;
        }
        int separator = firstSeparator(text);
        if (separator <= 0)
        {
            return;
        }
        String label = normalizeLabel(text.substring(0, separator));
        boolean longText = "careerSummary".equals(LABEL_FIELDS.get(label));
        String value = normalizeValue(text.substring(separator + 1), longText);
        applyField(label, value, profile, recognizedFields);
    }

    private static int firstSeparator(String text)
    {
        int colon = text.indexOf('：');
        int asciiColon = text.indexOf(':');
        if (colon < 0)
        {
            return asciiColon;
        }
        return asciiColon < 0 ? colon : Math.min(colon, asciiColon);
    }

    private static void applyField(String label, String value, ExpertProfile profile,
            Set<String> recognizedFields)
    {
        String field = LABEL_FIELDS.get(label);
        if (field == null || value == null || value.isBlank())
        {
            return;
        }
        switch (field)
        {
            case "realName" -> profile.setRealName(value);
            case "organization" -> profile.setOrganization(value);
            case "professionalTitle" -> profile.setProfessionalTitle(value);
            case "serviceRegion" -> profile.setServiceRegion(value);
            case "researchField" -> profile.setResearchField(value);
            case "industryField" -> profile.setIndustryField(value);
            case "phone" -> profile.setPhone(value);
            case "email" -> profile.setEmail(value);
            case "careerSummary" -> profile.setCareerSummary(value);
            default -> {
                return;
            }
        }
        recognizedFields.add(field);
    }

    private static String normalizeLabel(String value)
    {
        return value == null ? "" : value
                .replace('\u00A0', ' ')
                .replaceAll("[\\s*＊]+", "")
                .replaceAll("[：:]$", "")
                .trim();
    }

    private static String normalizeValue(String value, boolean preserveLines)
    {
        if (value == null)
        {
            return "";
        }
        String normalized = value.replace('\u00A0', ' ').trim();
        if (preserveLines)
        {
            return normalized.replaceAll("\\r\\n?", "\n");
        }
        return normalized.replaceAll("\\s+", " ");
    }

    private static void validate(ExpertProfile profile)
    {
        checkLength(profile.getRealName(), 64, "专家姓名");
        checkLength(profile.getOrganization(), 128, "所属单位");
        checkLength(profile.getProfessionalTitle(), 64, "职称职务");
        checkLength(profile.getServiceRegion(), 128, "服务区域");
        checkLength(profile.getResearchField(), 255, "专业方向");
        checkLength(profile.getIndustryField(), 255, "产业领域");
        checkLength(profile.getPhone(), 32, "联系电话");
        checkLength(profile.getEmail(), 128, "电子邮箱");
        checkLength(profile.getCareerSummary(), 2000, "专家简介");
        if (profile.getEmail() != null && !profile.getEmail().isBlank()
                && !EMAIL_PATTERN.matcher(profile.getEmail()).matches())
        {
            throw new ServiceException("Word模板中的电子邮箱格式不正确");
        }
    }

    private static void checkLength(String value, int maxLength, String fieldName)
    {
        if (value != null && value.length() > maxLength)
        {
            throw new ServiceException(fieldName + "不能超过" + maxLength + "个字符");
        }
    }
}
