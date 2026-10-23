package com.study.rag.retrieve;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReciprocalRankFusionTest {

    @Test
    void fusesRanksAndSkipsDuplicateIdsInOneList() {
        record Item(String id) {
        }
        List<Item> bm25 = List.of(new Item("a"), new Item("b"), new Item("a"));
        List<Item> knn = List.of(new Item("b"), new Item("c"));

        List<ReciprocalRankFusion.Fused<Item>> fused = ReciprocalRankFusion.fuse(List.of(bm25, knn), Item::id, 60);

        assertEquals(3, fused.size());
        assertEquals("b", fused.get(0).item().id());
        assertEquals("a", fused.get(1).item().id());
        assertEquals("c", fused.get(2).item().id());
        assertEquals(1.0d / 61 + 1.0d / 62, fused.get(0).score(), 1e-9);
        assertEquals(1.0d / 61, fused.get(1).score(), 1e-9);
        assertEquals(1.0d / 62, fused.get(2).score(), 1e-9);
    }
}
