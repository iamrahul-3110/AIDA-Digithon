package com.aida.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Token lookup and the HTTP-level security contract. */
@SpringBootTest
class TokenSecurityTest {

    static final String INIT = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
             "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    @Autowired
    WebApplicationContext context;

    @Autowired
    TokenRegistry registry;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registryFindsTokensByHashOnly() {
        assertThat(registry.find(TestTokens.FULL)).hasValueSatisfying(p -> {
            assertThat(p.name()).isEqualTo("test-full");
            assertThat(p.projects()).isEqualTo(Set.of("SHOP", "OPS"));
            assertThat(p.scopes()).isEqualTo(Set.of("mcp", "admin"));
        });
        assertThat(registry.find(TestTokens.OPS_ONLY)).hasValueSatisfying(p -> {
            assertThat(p.canRead("OPS")).isTrue();
            assertThat(p.canRead("shop")).isFalse();
        });
        assertThat(registry.find("test-token-ful")).isEmpty();
        assertThat(registry.find("")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
        // The hash itself is not a token.
        assertThat(registry.find("6decfc17ad896ea6fea15c90f2cfb7c0ef5b4bf7c3e6b130d9eb7fc7750d5c2f")).isEmpty();
    }

    @Test
    void noTokenIs401WithJsonBody() throws Exception {
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(INIT))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void unknownTokenIs401() throws Exception {
        mvc.perform(post("/mcp").header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON).content(INIT))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
        mvc.perform(post("/mcp").header("Authorization", "Basic " + TestTokens.FULL)
                        .contentType(MediaType.APPLICATION_JSON).content(INIT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutMcpScopeIs403() throws Exception {
        mvc.perform(post("/mcp").header("Authorization", "Bearer " + TestTokens.ADMIN_ONLY)
                        .contentType(MediaType.APPLICATION_JSON).content(INIT))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"forbidden\"}"));
    }

    @Test
    void adminNeedsAdminScope() throws Exception {
        mvc.perform(post("/admin/alerts/run")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/alerts/run").header("Authorization", "Bearer " + TestTokens.OPS_ONLY))
                .andExpect(status().isForbidden());
    }

    @Test
    void everythingElseIsDenied() throws Exception {
        mvc.perform(get("/")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").header("Authorization", "Bearer " + TestTokens.FULL))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/mock/team").param("project", "SHOP")).andExpect(status().isOk());
    }
}
