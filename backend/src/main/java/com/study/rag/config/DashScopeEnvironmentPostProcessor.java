package com.study.rag.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public class DashScopeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String apiKey = environment.getProperty("DASHSCOPE_API_KEY", "");
        if (apiKey.isBlank()) {
            apiKey = environment.getProperty("spring.ai.dashscope.api-key", "");
        }
        if (apiKey.isBlank()) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "dashscope-disabled",
                    Map.of("spring.ai.dashscope.enabled", "false")));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
