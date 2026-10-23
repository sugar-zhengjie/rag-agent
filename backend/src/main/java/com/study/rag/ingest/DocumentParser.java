package com.study.rag.ingest;

import com.study.rag.config.RagProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class DocumentParser {

    private static final Set<String> ALLOWED = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "html", "htm", "md", "markdown", "txt", "csv");

    private final RecursiveTextSplitter splitter;
    private final MarkdownChunker markdownChunker;
    private final SpreadsheetChunker spreadsheetChunker;
    private final int maxChunks;
    private final String strategy;

    @Autowired
    public DocumentParser(RagProperties properties) {
        RagProperties.Chunk chunk = properties.getChunk();
        this.splitter = new RecursiveTextSplitter(chunk.getTargetChars(), chunk.getOverlapChars(), chunk.getMinChars());
        this.markdownChunker = new MarkdownChunker(splitter);
        this.spreadsheetChunker = new SpreadsheetChunker(splitter);
        this.strategy = properties.getChunk().getStrategy();
        this.maxChunks = properties.getUpload().getMaxChunks();
    }

    public DocumentParser(RecursiveTextSplitter splitter) {
        this.splitter = splitter;
        this.markdownChunker = new MarkdownChunker(splitter);
        this.spreadsheetChunker = new SpreadsheetChunker(splitter);
        this.maxChunks = 2000;
        this.strategy = "structured";
    }

    public static String extensionOf(String fileName) {
        String base = fileName == null ? "" : Paths.get(fileName).getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return "";
        }
        return base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static void checkAllowed(String extension) {
        if (!ALLOWED.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + (extension.isBlank() ? "缺少扩展名" : extension));
        }
    }

    public List<ChunkDraft> parse(String fileName, byte[] bytes) {
        String extension = extensionOf(fileName);
        checkAllowed(extension);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("文件是空的");
        }
        List<ChunkDraft> drafts = switch (extension) {
            case "pdf" -> splitPages(readPdf(bytes));
            case "md", "markdown" -> markdownChunker.chunk(TextCleaner.clean(utf8(bytes)));
            case "csv" -> spreadsheetChunker.chunkCsv(utf8(bytes), "csv");
            case "xls", "xlsx" -> spreadsheetChunker.chunkExcel(bytes);
            case "txt" -> splitPlain(TextCleaner.clean(utf8(bytes)));
            case "html", "htm", "doc", "docx", "ppt", "pptx" -> splitPlain(TextCleaner.clean(readTika(bytes)));
            default -> throw new IllegalArgumentException("不支持的文件类型: " + extension);
        };
        List<ChunkDraft> reindexed = reindex(ChunkStrategies.retarget(strategy, drafts, splitter));
        if (reindexed.isEmpty()) {
            throw new IllegalArgumentException("没有解析出可用文本");
        }
        if (reindexed.size() > maxChunks) {
            throw new IllegalArgumentException("切片数量超过 " + maxChunks + "，请拆小文件后再入库");
        }
        return reindexed;
    }

    private List<ChunkDraft> splitPages(List<PageText> pages) {
        List<ChunkDraft> drafts = new ArrayList<>();
        for (PageText page : pages) {
            for (String piece : splitter.split(page.text())) {
                drafts.add(new ChunkDraft(piece, page.page(), null, null, drafts.size()));
            }
        }
        return drafts;
    }

    private List<ChunkDraft> splitPlain(String text) {
        List<ChunkDraft> drafts = new ArrayList<>();
        for (String piece : splitter.split(text)) {
            drafts.add(new ChunkDraft(piece, null, null, null, drafts.size()));
        }
        return drafts;
    }

    private static List<PageText> readPdf(byte[] bytes) {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int count = document.getNumberOfPages();
            for (int page = 1; page <= count; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = TextCleaner.clean(stripper.getText(document));
                if (text != null && !text.isBlank()) {
                    pages.add(new PageText(page, text));
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("PDF 解析失败: " + ex.getMessage());
        }
        return pages;
    }

    private static String readTika(byte[] bytes) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(10 * 1024 * 1024);
            Metadata metadata = new Metadata();
            parser.parse(new ByteArrayInputStream(bytes), handler, metadata, new ParseContext());
            return handler.toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("文档解析失败: " + ex.getMessage());
        }
    }

    private static String utf8(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            return text.substring(1);
        }
        return text;
    }

    private static List<ChunkDraft> reindex(List<ChunkDraft> drafts) {
        List<ChunkDraft> reindexed = new ArrayList<>();
        for (ChunkDraft draft : drafts) {
            if (draft.content() == null || draft.content().isBlank()) {
                continue;
            }
            reindexed.add(new ChunkDraft(draft.content().trim(), draft.page(), draft.sheet(), draft.heading(), reindexed.size(), draft.parentContent()));
        }
        return reindexed;
    }

    private record PageText(int page, String text) {
    }
}
