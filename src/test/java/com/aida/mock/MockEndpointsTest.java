package com.aida.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.aida.contract.Statuses;
import com.jayway.jsonpath.JsonPath;

/** The mock Jira and calendar endpoints, through the real security filter chain. */
@SpringBootTest
class MockEndpointsTest {

    static final String SEARCH = "/mock/jira/rest/api/2/search";
    static final String EVENTS = "/mock/calendar/events";

    @Autowired
    WebApplicationContext context;

    @Autowired
    MockStore store;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        store.reset();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    long count(String project, String fixVersion) {
        return store.dataset().issues().stream()
                .filter(i -> i.project().equals(project) && (fixVersion == null || fixVersion.equals(i.fixVersion())))
                .count();
    }

    @Test
    void searchReturnsJiraShape() throws Exception {
        mvc.perform(get(SEARCH).param("jql", "project=SHOP AND fixVersion=\"3.2\"").param("maxResults", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value(0))
                .andExpect(jsonPath("$.maxResults").value(100))
                .andExpect(jsonPath("$.total").value((int) count("SHOP", "3.2")))
                .andExpect(jsonPath("$.issues[*].key", everyItem(startsWith("SHOP-"))))
                .andExpect(jsonPath("$.issues[*].fields.fixVersions[0].name", everyItem(is("3.2"))))
                .andExpect(jsonPath("$.issues[0].fields.summary").isString())
                .andExpect(jsonPath("$.issues[0].fields.issuetype.name").isString())
                .andExpect(jsonPath("$.issues[0].fields.status.name").isString())
                .andExpect(jsonPath("$.issues[0].fields.parent.key").isString())
                .andExpect(jsonPath("$.issues[0].fields.parent.fields.summary").isString())
                .andExpect(jsonPath("$.issues[*].fields.created",
                        everyItem(matchesPattern("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}\\+0530"))))
                .andExpect(jsonPath("$.issues[*].changelog.histories[*].items[0].field", everyItem(is("status"))));
    }

    @Test
    void searchMapsEveryFieldOfAnIssue() throws Exception {
        var issue = store.dataset().issues().stream()
                .filter(i -> i.status().equals(Statuses.DONE) && "3.2".equals(i.fixVersion())).findFirst().orElseThrow();
        String json = mvc.perform(get(SEARCH).param("jql", "project=SHOP AND fixVersion=\"3.2\"")
                .param("maxResults", "100")).andReturn().getResponse().getContentAsString();
        String path = "$.issues[?(@.key == '" + issue.key() + "')]";

        assertThat(JsonPath.<List<String>>read(json, path + ".fields.status.name")).containsExactly("Done");
        assertThat(JsonPath.<List<Object>>read(json, path + ".fields.resolutiondate").getFirst()).isNotNull();
        assertThat(JsonPath.<List<Double>>read(json, path + ".fields.customfield_10016"))
                .containsExactly(issue.estimate());
        assertThat(JsonPath.<List<String>>read(json, path + ".fields.assignee.displayName"))
                .containsExactly(issue.assignee());
        assertThat(JsonPath.<List<String>>read(json, path + ".fields.parent.fields.summary"))
                .containsExactly(issue.epicName());
        assertThat(JsonPath.<List<String>>read(json, path + ".changelog.histories[*].items[0].toString"))
                .containsExactlyElementsOf(issue.statusHistory().stream().skip(1).map(s -> s.status()).toList());
    }

    @Test
    void searchPaginates() throws Exception {
        long total = count("SHOP", null);
        String first = mvc.perform(get(SEARCH).param("jql", "project=SHOP").param("startAt", "0")
                .param("maxResults", "100")).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(get(SEARCH).param("jql", "project=SHOP").param("startAt", "100")
                .param("maxResults", "100")).andReturn().getResponse().getContentAsString();

        List<String> keys1 = JsonPath.read(first, "$.issues[*].key");
        List<String> keys2 = JsonPath.read(second, "$.issues[*].key");
        assertThat(keys1).hasSize(100);
        assertThat(keys2).hasSize((int) total - 100).doesNotContainAnyElementsOf(keys1);
        assertThat(JsonPath.<Integer>read(second, "$.total")).isEqualTo((int) total);

        mvc.perform(get(SEARCH).param("jql", "project=SHOP").param("maxResults", "500"))
                .andExpect(jsonPath("$.maxResults").value(100))
                .andExpect(jsonPath("$.issues", hasSize(100)));
        mvc.perform(get(SEARCH).param("jql", "project=SHOP").param("startAt", "5000"))
                .andExpect(jsonPath("$.issues", hasSize(0)));
    }

    @Test
    void searchFiltersByJql() throws Exception {
        mvc.perform(get(SEARCH).param("jql", "project = OPS").param("maxResults", "100"))
                .andExpect(jsonPath("$.total").value((int) count("OPS", null)))
                .andExpect(jsonPath("$.issues[*].key", everyItem(startsWith("OPS-"))));
        mvc.perform(get(SEARCH).param("jql", "project=SHOP AND fixVersion=3.3"))
                .andExpect(jsonPath("$.total").value((int) count("SHOP", "3.3")));
        mvc.perform(get(SEARCH).param("jql", "project=NOPE")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(SEARCH).param("jql", "assignee = Arjun")).andExpect(status().isBadRequest());
    }

    @Test
    void versions() throws Exception {
        mvc.perform(get("/mock/jira/rest/api/2/project/SHOP/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("3.1", "3.2", "3.3")))
                .andExpect(jsonPath("$[1].releaseDate").value(store.asOf().plusDays(25).toString()))
                .andExpect(jsonPath("$[1].released").value(false))
                .andExpect(jsonPath("$[0].released").value(true));
        mvc.perform(get("/mock/jira/rest/api/2/project/OPS/versions")).andExpect(jsonPath("$[0].name").value("Q4"));
    }

    @Test
    void team() throws Exception {
        mvc.perform(get("/mock/team").param("project", "SHOP"))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].name").value("Arjun"))
                .andExpect(jsonPath("$[0].country").value("IN"));
        mvc.perform(get("/mock/team").param("project", "OPS")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void calendarAddListAndDelete() throws Exception {
        String from = store.asOf().toString();
        String to = store.asOf().plusMonths(3).toString();
        String created = mvc.perform(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"person\":\"Arjun\",\"start\":\"2026-11-02\",\"end\":\"2026-11-06\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/mock/calendar/events/lv-")))
                .andExpect(jsonPath("$.person").value("Arjun"))
                .andExpect(jsonPath("$.country").value("IN"))
                .andExpect(jsonPath("$.type").value("leave"))
                .andExpect(jsonPath("$.end").value("2026-11-06"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get(EVENTS).param("from", from).param("to", to))
                .andExpect(jsonPath("$.events[?(@.id == '" + id + "')].person").value("Arjun"));
        mvc.perform(get(EVENTS).param("from", "2026-11-04").param("to", "2026-11-04"))
                .andExpect(jsonPath("$.events[?(@.id == '" + id + "')]", hasSize(1)));

        mvc.perform(delete(EVENTS + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(EVENTS).param("from", from).param("to", to))
                .andExpect(jsonPath("$.events[?(@.id == '" + id + "')]", hasSize(0)));
        mvc.perform(delete(EVENTS + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void calendarRejectsBadLeave() throws Exception {
        mvc.perform(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"person\":\"Nobody\",\"start\":\"2026-11-02\",\"end\":\"2026-11-06\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"person\":\"Arjun\",\"start\":\"2026-11-06\",\"end\":\"2026-11-02\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetRegeneratesAndDropsAddedLeaves() throws Exception {
        int before = store.dataset().absences().size();
        mvc.perform(post(EVENTS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"person\":\"Linh\",\"start\":\"2026-11-09\",\"end\":\"2026-11-10\"}"))
                .andExpect(status().isCreated());
        assertThat(store.dataset().absences()).hasSize(before + 1);

        mvc.perform(post("/mock/admin/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-10-27"))
                .andExpect(jsonPath("$.seed").value(7))
                .andExpect(jsonPath("$.issues").value(store.dataset().issues().size()));
        assertThat(store.dataset()).isEqualTo(DataGenerator.generate(store.asOf(), 7));
    }

    @Test
    void consolePageIsServed() throws Exception {
        mvc.perform(get("/mock/console.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AIDA leave console")));
    }

    @Test
    void routesOutsideTheMockAreDenied() throws Exception {
        mvc.perform(get("/mcp")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
