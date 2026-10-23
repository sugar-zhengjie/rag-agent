package com.study.rag.ingest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserTest {

    private final DocumentParser parser = new DocumentParser(new RecursiveTextSplitter(800, 40, 2));

    @Test
    void splitsMarkdownAndCsvByStructure() {
        List<ChunkDraft> markdown = parser.parse("手册.md", """
                # 报销
                差旅需要发票。
                """.getBytes(StandardCharsets.UTF_8));
        assertEquals("报销", markdown.get(0).heading());
        assertTrue(markdown.get(0).content().contains("差旅需要发票"));

        List<ChunkDraft> csv = parser.parse("名单.csv", """
                姓名,部门
                张三,研发
                李四,财务
                """.getBytes(StandardCharsets.UTF_8));
        assertEquals("csv", csv.get(0).sheet());
        assertTrue(csv.get(0).content().contains("姓名 | 部门"));
        assertTrue(csv.get(0).content().contains("张三 | 研发"));
    }

    @Test
    void readsPdfPageAndExcelSheet() throws Exception {
        List<ChunkDraft> pdf = parser.parse("note.pdf", onePagePdf("Hello RAG page"));
        assertEquals(1, pdf.get(0).page());
        assertTrue(pdf.get(0).content().contains("Hello RAG page"));

        List<ChunkDraft> excel = parser.parse("表.xlsx", workbook());
        assertEquals("人员", excel.get(0).sheet());
        assertTrue(excel.get(0).content().contains("王五"));
    }

    @Test
    void readsHtmlAndRejectsUnknownTypes() {
        List<ChunkDraft> html = parser.parse("page.html",
                "<html><body><p>你好，检索增强</p></body></html>".getBytes(StandardCharsets.UTF_8));
        assertFalse(html.isEmpty());
        assertTrue(html.get(0).content().contains("你好"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("archive.zip", "abc".getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] onePagePdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("人员");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("城市");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("王五");
            row.createCell(1).setCellValue("杭州");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
