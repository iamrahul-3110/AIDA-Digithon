package com.aida.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider aidaToolCallbackProvider(AidaTools aidaTools) {
        return MethodToolCallbackProvider.builder().toolObjects(aidaTools).build();
    }
}
