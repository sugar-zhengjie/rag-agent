package com.study.rag.chat;

import com.study.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationMemory {

    private final int maxTurns;
    private final ConcurrentHashMap<String, Deque<Turn>> sessions = new ConcurrentHashMap<>();

    public ConversationMemory(RagProperties properties) {
        this.maxTurns = Math.max(1, properties.getMemory().getMaxTurns());
    }

    public List<Turn> history(String userId, String sessionId) {
        Deque<Turn> turns = sessions.get(key(userId, sessionId));
        if (turns == null) {
            return List.of();
        }
        synchronized (turns) {
            return List.copyOf(turns);
        }
    }

    public void append(String userId, String sessionId, String question, String evidence) {
        Deque<Turn> turns = sessions.computeIfAbsent(key(userId, sessionId), id -> new ArrayDeque<>());
        synchronized (turns) {
            turns.addLast(new Turn(question, evidence));
            while (turns.size() > maxTurns) {
                turns.removeFirst();
            }
        }
    }

    public void clear(String userId, String sessionId) {
        sessions.remove(key(userId, sessionId));
    }

    private static String key(String userId, String sessionId) {
        String user = userId == null || userId.isBlank() ? "owner" : userId.trim();
        return user + ":" + normalize(sessionId);
    }

    private static String normalize(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "default";
        }
        return sessionId.trim();
    }
}
