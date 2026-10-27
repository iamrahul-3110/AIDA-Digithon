package com.aida.mock;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo controls for the mock data. */
@RestController
@Profile("mock")
@RequestMapping("/mock/admin")
public class MockAdminController {

    private final MockStore store;

    public MockAdminController(MockStore store) {
        this.store = store;
    }

    /** Regenerates the demo data, dropping any leaves added through the console. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Dataset dataset = store.reset();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("asOf", store.asOf().toString());
        summary.put("seed", store.seed());
        summary.put("issues", dataset.issues().size());
        summary.put("versions", dataset.versions().size());
        summary.put("absences", dataset.absences().size());
        return summary;
    }
}
