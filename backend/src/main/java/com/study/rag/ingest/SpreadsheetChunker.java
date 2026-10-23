package com.study.rag.ingest;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public class SpreadsheetChunker {

    private final RecursiveTextSplitter splitter;

    public SpreadsheetChunker(RecursiveTextSplitter splitter) {
        this.splitter = splitter;
    }

    public List<ChunkDraft> chunkCsv(String csv, String sheetName) {
        return pack(sheetName, parseCsv(csv));
    }

    public List<ChunkDraft> chunkExcel(byte[] bytes) {
        DataFormatter formatter = new DataFormatter();
        List<ChunkDraft> drafts = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                drafts.addAll(pack(sheet.getSheetName(), readSheet(sheet, formatter)));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Excel 解析失败", ex);
        }
        return reindex(drafts);
    }

    public List<ChunkDraft> pack(String sheet, List<List<String>> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        String headerLine = join(rows.get(0));
        if (headerLine.isBlank() && rows.size() == 1) {
            return List.of();
        }
        List<ChunkDraft> drafts = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        int target = splitter.targetChars();
        for (int i = 1; i < rows.size(); i++) {
            String line = join(rows.get(i));
            if (line.isBlank()) {
                continue;
            }
            String single = headerLine.isBlank() ? line : headerLine + "\n" + line;
            if (single.length() > target) {
                flushBody(drafts, sheet, headerLine, body);
                emit(drafts, sheet, single);
                continue;
            }
            int nextLength = headerLine.length() + (body.isEmpty() ? 0 : 1 + body.length()) + 1 + line.length();
            if (!body.isEmpty() && nextLength > target) {
                flushBody(drafts, sheet, headerLine, body);
            }
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append(line);
        }
        flushBody(drafts, sheet, headerLine, body);
        return reindex(drafts);
    }

    static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String text = csv.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(current);
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (current == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(current);
            }
        }
        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private void flushBody(List<ChunkDraft> drafts, String sheet, String headerLine, StringBuilder body) {
        if (body.isEmpty()) {
            return;
        }
        String content = headerLine.isBlank() ? body.toString() : headerLine + "\n" + body;
        body.setLength(0);
        emit(drafts, sheet, content);
    }

    private void emit(List<ChunkDraft> drafts, String sheet, String content) {
        for (String piece : splitter.split(content)) {
            drafts.add(new ChunkDraft(piece, null, sheet, null, drafts.size()));
        }
    }

    private static List<List<String>> readSheet(Sheet sheet, DataFormatter formatter) {
        List<List<String>> rows = new ArrayList<>();
        int last = sheet.getLastRowNum();
        int width = 0;
        for (int i = 0; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                width = Math.max(width, row.getLastCellNum());
            }
        }
        for (int i = 0; i <= last; i++) {
            Row row = sheet.getRow(i);
            List<String> cells = new ArrayList<>();
            boolean any = false;
            for (int c = 0; c < width; c++) {
                String value = "";
                if (row != null) {
                    Cell cell = row.getCell(c);
                    value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                }
                if (!value.isEmpty()) {
                    any = true;
                }
                cells.add(value);
            }
            if (any) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private static String join(List<String> cells) {
        return String.join(" | ", cells).trim();
    }

    private static List<ChunkDraft> reindex(List<ChunkDraft> drafts) {
        List<ChunkDraft> reindexed = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            reindexed.add(new ChunkDraft(draft.content(), draft.page(), draft.sheet(), draft.heading(), i));
        }
        return reindexed;
    }
}
