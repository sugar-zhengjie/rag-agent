package com.study.rag.retrieve;

public record StageTiming(long retrievalMs, long rerankMs, int contextChars) {
}
