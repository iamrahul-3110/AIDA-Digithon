package com.aida.alerts;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /admin/alerts/run}: run the alert check now (needs a token with scope admin). */
@RestController
@RequestMapping("/admin/alerts")
public class AlertController {

    /** Turns on the {@code @Scheduled} alert run. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class Scheduling {
    }

    private final AlertService alerts;

    public AlertController(AlertService alerts) {
        this.alerts = alerts;
    }

    @PostMapping("/run")
    public AlertService.RunResult run() {
        return alerts.run();
    }
}
