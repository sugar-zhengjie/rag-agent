package com.study.rag.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleQuery {

    private static final Pattern ERROR_CODE = Pattern.compile("(?i)err[\\s-]*(\\d{3,5})");
    private static final Pattern SKU = Pattern.compile("(?i)sku[\\s-]*([a-z]{2})[\\s-]*(\\d{3})");
    private static final Pattern INTENT = Pattern.compile("另外|还有|以及|同时");

    private RuleQuery() {
    }

    public static String normalize(String question) {
        String text = question == null ? "" : question.trim();
        Matcher error = ERROR_CODE.matcher(text);
        text = error.replaceAll("ERR-$1");
        Matcher sku = SKU.matcher(text);
        StringBuilder rewritten = new StringBuilder();
        while (sku.find()) {
            sku.appendReplacement(rewritten, "SKU-" + sku.group(1).toUpperCase(Locale.ROOT) + "-" + sku.group(2));
        }
        sku.appendTail(rewritten);
        text = rewritten.toString();
        if (text.contains("多少钱") && (text.contains("住宿") || text.contains("补贴"))) {
            text = text.replace("多少钱", "标准是多少");
        }
        return text;
    }

    public static List<String> splitIntents(String question) {
        String text = question == null ? "" : question.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        String[] parts = INTENT.split(text);
        List<String> queries = new ArrayList<>();
        for (String part : parts) {
            String query = part.trim().replaceAll("^[，,、\\s]+|[，,、\\s]+$", "");
            if (query.length() >= 2) {
                queries.add(query);
            }
        }
        return queries.isEmpty() ? List.of(text) : queries;
    }
}
