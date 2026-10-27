package com.aida.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;

import com.aida.contract.Forecast;
import com.aida.security.TestTokens;

/** The M0 seams other milestones build on: tool registration through AidaToolSet, and the ToolSupport meta block. */
@SpringBootTest
class SeamsTest {

    static class PingTools implements AidaToolSet {
        @Tool(name = "ping", description = "Test tool")
        public String ping() {
            return "pong";
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        PingTools pingTools() {
            return new PingTools();
        }
    }

    @Autowired
    ToolCallbackProvider aidaTools;

    @Autowired
    ToolSupport toolSupport;

    @Test
    void everyAidaToolSetBeanIsRegistered() {
        assertThat(Arrays.stream(aidaTools.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .contains("ping");
    }

    @Test
    void toolSupportAddsMetaBlockWithDataWindow() {
        LocalDate d = LocalDate.of(2026, 10, 27);
        Forecast forecast = new Forecast("SHOP", "3.2", d, 20, d.plusDays(20), d.plusDays(32), d.plusDays(40),
                d.plusDays(25), 0.45, 12, d.minusWeeks(12), d, 0.95, 10_000, 42L);

        SecurityContextHolder.getContext().setAuthentication(
                TestTokens.authentication("seams", Set.of("SHOP"), Set.of("mcp")));
        Map<String, Object> result;
        try {
            result = toolSupport.call("forecast_delivery", "SHOP", Map.of("project", "SHOP"), () -> forecast);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(result).containsEntry("p85", "2026-11-28").containsEntry("pByTarget", 0.45);
        assertThat(result.get("meta")).isInstanceOfSatisfying(Map.class, meta -> assertThat(meta)
                .containsEntry("tool", "forecast_delivery")
                .containsEntry("windowStart", "2026-08-04")
                .containsEntry("windowEnd", "2026-10-27")
                .containsKey("calledAt"));
    }
}
