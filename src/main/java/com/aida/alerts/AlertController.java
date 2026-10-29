package com.aida.alerts;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/admin/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> runNow() {
        alertService.runAlerts();
        return ResponseEntity.ok(Map.of("status", "alerts run complete"));
    }
}
