package com.aida.mock;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Mock admin reset endpoint.
 */
@RestController
@RequestMapping("/mock/admin")
public class MockAdminController {

    private final DataGenerator generator;

    public MockAdminController(DataGenerator generator) {
        this.generator = generator;
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        generator.reset();
        return Map.of("status", "reset complete");
    }
}
