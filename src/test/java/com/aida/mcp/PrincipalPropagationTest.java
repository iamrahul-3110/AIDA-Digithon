package com.aida.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;

import com.aida.security.TestTokens;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;

/** A tool run on another thread pool still sees the caller's token, so the project check never silently drops. */
@SpringBootTest
class PrincipalPropagationTest {

    /** Echoes the caller's token name, through ToolSupport and its access check. */
    static class WhoAmITools implements AidaToolSet {
        private final ToolSupport support;

        WhoAmITools(ToolSupport support) {
            this.support = support;
        }

        @Tool(name = "whoami_shop", description = "Test tool")
        public Map<String, Object> whoami() {
            return support.call("whoami_shop", "SHOP", Map.of(), () -> Map.of("caller", ToolSupport.currentPrincipal().name()));
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        WhoAmITools whoAmITools(ToolSupport support) {
            return new WhoAmITools(support);
        }
    }

    @Autowired
    ToolCallbackProvider aidaTools;

    final ExecutorService otherPool = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        otherPool.shutdownNow();
        SecurityContextHolder.clearContext();
    }

    ToolCallback whoami() {
        return Arrays.stream(aidaTools.getToolCallbacks())
                .filter(c -> c.getToolDefinition().name().equals("whoami_shop")).findFirst().orElseThrow();
    }

    /** What the MCP server hands a tool: the exchange whose transport context the HTTP request filled. */
    static ToolContext mcpContext(String tokenName, Set<String> projects) {
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.transportContext()).thenReturn(McpTransportContext.create(Map.of(ToolConfig.AUTHENTICATION_KEY,
                TestTokens.authentication(tokenName, projects, Set.of("mcp")))));
        return new ToolContext(Map.of(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, exchange));
    }

    @Test
    void principalReachesToolOnAnotherThread() throws Exception {
        ToolContext context = mcpContext("pool-caller", Set.of("SHOP"));
        String result = otherPool.submit(() -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            return whoami().call("{}", context);
        }).get();

        assertThat(result).contains("\"caller\":\"pool-caller\"");
    }

    @Test
    void projectCheckStillAppliesOnAnotherThread() {
        ToolContext context = mcpContext("ops-caller", Set.of("OPS"));
        assertThatThrownBy(() -> otherPool.submit(() -> whoami().call("{}", context)).get())
                .isInstanceOf(ExecutionException.class)
                .rootCause().hasMessageContaining("Access denied: token 'ops-caller' cannot read project SHOP");
    }

    @Test
    void withoutTheTransportContextTheToolIsDenied() {
        assertThatThrownBy(() -> otherPool.submit(() -> whoami().call("{}", new ToolContext(Map.of()))).get())
                .rootCause().hasMessageContaining("Access denied");
    }

    @Test
    void callerThreadContextIsRestoredAfterTheCall() throws Exception {
        ToolContext context = mcpContext("pool-caller", Set.of("SHOP"));
        otherPool.submit(() -> whoami().call("{}", context)).get();
        assertThat(otherPool.submit(() -> SecurityContextHolder.getContext().getAuthentication()).get()).isNull();
    }
}
