package com.study.rag.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class EvalDataset {

    private final ObjectMapper objectMapper;
    private List<EvalCase> cases;

    public EvalDataset(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<EvalCase> cases() {
        if (cases == null) {
            cases = List.copyOf(read());
        }
        return cases;
    }

    public List<EvalCase> read() {
        try (InputStream input = new ClassPathResource("eval/dataset.json").getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("评估集读取失败: " + ex.getMessage(), ex);
        }
    }
}
