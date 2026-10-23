package com.study.rag.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecursiveTextSplitterTest {

    @Test
    void hardWindowKeepsOverlap() {
        RecursiveTextSplitter splitter = new RecursiveTextSplitter(10, 3, 1);
        List<String> chunks = splitter.split("abcdefghijklmnopqrstuvwxyz");

        assertEquals("abcdefghij", chunks.get(0));
        assertEquals("hijklmnopq", chunks.get(1));
        assertTrue(chunks.get(1).startsWith("hij"));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 10));
    }

    @Test
    void packsChineseSentencesAndDropsTinyFragments() {
        RecursiveTextSplitter splitter = new RecursiveTextSplitter(20, 0, 2);
        List<String> chunks = splitter.split("这是第一句。这是第二句。这是第三句。这是第四句。");

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.get(0).contains("第一"));
        assertTrue(chunks.get(chunks.size() - 1).contains("第四"));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 20));
        assertTrue(splitter.split("短").isEmpty());
    }
}
