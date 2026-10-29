package com.aida.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired MockMvc mvc;

    @Test void noToken_returns401() throws Exception {
        mvc.perform(post("/mcp"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test void unknownToken_returns401() throws Exception {
        mvc.perform(post("/mcp")
                .header("Authorization", "Bearer totally-invalid-token-xyz"))
            .andExpect(status().isUnauthorized());
    }

    @Test void mockEndpoints_accessible_withoutToken() throws Exception {
        mvc.perform(get("/mock/team").param("project", "SHOP"))
            .andExpect(status().isOk());
    }

    @Test void actuatorHealth_accessible_withoutToken() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }
}
