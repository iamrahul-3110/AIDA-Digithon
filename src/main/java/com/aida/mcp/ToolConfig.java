package com.aida.mcp;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.aida.engine.ExplainEngine;
import com.aida.engine.ForecastEngine;
import com.aida.engine.QualityEngine;
import com.aida.engine.ReportEngine;
import com.aida.engine.RiskEngine;
import com.aida.engine.WhatIfEngine;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exposes every {@link AidaToolSet} bean as MCP tools, and the pure engines as beans the tools inject.
 *
 * <p>Security context propagation: the MCP server may run a tool on another thread than the HTTP request. The
 * transport captures the request's authentication into the MCP transport context, and every tool callback is
 * wrapped to restore it around the call, so {@link ToolSupport} always sees the caller's token.
 */
@Configuration(proxyBeanMethods = false)
public class ToolConfig {

    /** Transport-context key holding the caller's {@link Authentication}. */
    static final String AUTHENTICATION_KEY = "aida.authentication";

    @Bean
    ToolCallbackProvider aidaTools(List<AidaToolSet> toolSets) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(toolSets.toArray()).build()
                .getToolCallbacks();
        return ToolCallbackProvider.from(Arrays.stream(callbacks).map(SecurityContextToolCallback::new).toList());
    }

    /** The streamable HTTP transport from Spring AI's auto-configuration, plus the authentication capture. */
    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(JsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties) {
        WebMvcStreamableServerTransportProvider.Builder builder = WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(request -> {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    return auth == null ? McpTransportContext.EMPTY
                            : McpTransportContext.create(Map.of(AUTHENTICATION_KEY, auth));
                });
        if (properties.getKeepAliveInterval() != null) {
            builder.keepAliveInterval(properties.getKeepAliveInterval());
        }
        return builder.build();
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ForecastEngine forecastEngine() {
        return new ForecastEngine();
    }

    @Bean
    QualityEngine qualityEngine() {
        return new QualityEngine();
    }

    @Bean
    ExplainEngine explainEngine() {
        return new ExplainEngine();
    }

    @Bean
    WhatIfEngine whatIfEngine() {
        return new WhatIfEngine();
    }

    @Bean
    RiskEngine riskEngine() {
        return new RiskEngine();
    }

    @Bean
    ReportEngine reportEngine() {
        return new ReportEngine();
    }

    /** Runs a tool with the caller's authentication from the MCP exchange set on the current thread. */
    static final class SecurityContextToolCallback implements ToolCallback {

        private final ToolCallback delegate;

        SecurityContextToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            Authentication auth = toolContext == null ? null : McpToolUtils.getMcpExchange(toolContext)
                    .map(exchange -> exchange.transportContext().get(AUTHENTICATION_KEY))
                    .filter(Authentication.class::isInstance)
                    .map(Authentication.class::cast)
                    .orElse(null);
            if (auth == null) {
                return delegate.call(toolInput, toolContext);
            }
            SecurityContext previous = SecurityContextHolder.getContext();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            try {
                return delegate.call(toolInput, toolContext);
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        }
    }
}
