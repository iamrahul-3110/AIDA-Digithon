package com.aida.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MockEndpointsTest {

    @Autowired MockMvc mvc;

    @Test void jiraSearch_returnsJiraShape() throws Exception {
        mvc.perform(get("/mock/jira/rest/api/2/search")
                .param("jql", "project=SHOP AND fixVersion=\"3.2\"")
                .param("startAt", "0").param("maxResults", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.startAt").value(0))
            .andExpect(jsonPath("$.issues").isArray())
            .andExpect(jsonPath("$.issues[0].key").exists())
            .andExpect(jsonPath("$.issues[0].fields.status.name").exists());
    }

    @Test void jiraSearch_filtersByFixVersion() throws Exception {
        var result = mvc.perform(get("/mock/jira/rest/api/2/search")
                .param("jql", "project=SHOP AND fixVersion=\"3.1\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issues").isArray())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("3.1");
    }

    @Test void jiraVersions_returnsVersions() throws Exception {
        mvc.perform(get("/mock/jira/rest/api/2/project/SHOP/versions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").exists())
            .andExpect(jsonPath("$[0].releaseDate").exists());
    }

    @Test void team_returnsSHOPTeam() throws Exception {
        mvc.perform(get("/mock/team").param("project", "SHOP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").exists())
            .andExpect(jsonPath("$[0].country").exists());
    }

    @Test void calendar_returnsEvents() throws Exception {
        mvc.perform(get("/mock/calendar/events")
                .param("from", "2026-01-01").param("to", "2027-12-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events").isArray());
    }

    @Test void calendar_addAndDelete() throws Exception {
        String body = """
            {"person":"Arjun","start":"2026-12-10","end":"2026-12-12"}
            """;
        var createResult = mvc.perform(post("/mock/calendar/events")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
            .readTree(createResult.getResponse().getContentAsString()).path("id").asText();

        mvc.perform(delete("/mock/calendar/events/" + id))
            .andExpect(status().isNoContent());
    }

    @Test void reset_returnsOk() throws Exception {
        mvc.perform(post("/mock/admin/reset"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("reset complete"));
    }
}
