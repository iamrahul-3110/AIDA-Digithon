package com.aida.chat;

import com.aida.mcp.AidaTools;
import com.aida.security.TokenPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AidaTools aidaTools;

    public ChatController(AidaTools aidaTools) {
        this.aidaTools = aidaTools;
    }

    public record ChatRequest(String message, String project, String fixVersion, String audience, String scenario) {}
    public record ChatResponse(String answer, String toolCalled, Object result, Map<String, Object> metrics) {}

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req) {
        // Ensure security context is populated for the tools
        var currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth == null || !(currentAuth.getPrincipal() instanceof TokenPrincipal)) {
            var tp = new TokenPrincipal("web-chat", Set.of("SHOP", "OPS"), Set.of("mcp", "admin"));
            var auth = new UsernamePasswordAuthenticationToken(tp, null,
                    List.of(new SimpleGrantedAuthority("SCOPE_mcp"), new SimpleGrantedAuthority("SCOPE_admin")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        String msg = req.message() != null ? req.message().toLowerCase().trim() : "";
        String proj = (req.project() != null && !req.project().isBlank()) ? req.project().toUpperCase() :
                      (msg.contains("ops") ? "OPS" : "SHOP");
        String release = (req.fixVersion() != null && !req.fixVersion().isBlank()) ? req.fixVersion() : "3.2";

        String toolCalled;
        Map<String, Object> result;
        String answer;
        Map<String, Object> metrics = new LinkedHashMap<>();

        if (msg.contains("quality") || msg.contains("clean") || msg.contains("hygiene")) {
            toolCalled = "check_data_quality";
            result = aidaTools.check_data_quality(proj);
            Integer score = (Integer) result.get("score");
            Boolean allowed = (Boolean) result.get("forecastAllowed");
            metrics.put("score", score);
            metrics.put("forecastAllowed", allowed);
            answer = String.format("Data quality score for **%s** is **%d/100** (%s). Forecast allowed: **%s**.",
                    proj, score != null ? score : 0,
                    (score != null && score >= 70) ? "Good" : (score != null && score >= 50) ? "Fair" : "Poor",
                    Boolean.TRUE.equals(allowed) ? "Yes" : "No (blockers exist)");

        } else if (msg.contains("what if") || msg.contains("drop") || msg.contains("scenario")) {
            toolCalled = "run_what_if";
            String dropEpic = req.scenario() != null ? req.scenario() : "Gift cards";
            result = aidaTools.run_what_if(proj, release, dropEpic, null, null, null);
            Object forecastObj = result.get("forecast");
            answer = String.format("Simulation for scenario (drop: **%s**) on **%s %s** completed successfully.\nResult: %s",
                    dropEpic, proj, release, forecastObj != null ? forecastObj.toString() : "Simulation complete");

        } else if (msg.contains("risk") || msg.contains("blocker") || msg.contains("bottleneck")) {
            toolCalled = "get_risks";
            result = aidaTools.get_risks(proj);
            Object reportObj = result.get("report");
            answer = String.format("Identified risks and delivery assessment for project **%s**:\n%s",
                    proj, reportObj != null ? reportObj.toString() : "No critical risks found.");

        } else if (msg.contains("explain") || msg.contains("why") || msg.contains("change") || msg.contains("shift") || msg.contains("driver")) {
            toolCalled = "explain_change";
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusWeeks(2);
            result = aidaTools.explain_change(proj, release, from.toString(), to.toString());
            Object expObj = result.get("explanation");
            answer = String.format("Schedule shift and delivery drivers analysis for **%s %s** (window: %s to %s):\n%s",
                    proj, release, from, to, expObj != null ? expObj.toString() : "Schedule is stable.");

        } else if (msg.contains("report") || msg.contains("status")) {
            toolCalled = "build_report";
            String aud = (req.audience() != null && !req.audience().isBlank()) ? req.audience() : "client";
            result = aidaTools.build_report(proj, release, aud);
            Object rpt = result.get("report");
            answer = String.format("Status report generated for **%s %s** (Audience: **%s**):\n\n%s",
                    proj, release, aud, rpt != null ? rpt.toString() : "Report generated.");

        } else {
            // Default to forecast_delivery
            toolCalled = "forecast_delivery";
            result = aidaTools.forecast_delivery(proj, release, null, null);
            com.aida.contract.Forecast fc = (com.aida.contract.Forecast) result.get("forecast");
            if (fc != null) {
                int confPct = (int) Math.round(fc.pByTarget() * 100);
                metrics.put("confidence", confPct);
                metrics.put("p85Date", fc.p85().toString());
                metrics.put("p50Date", fc.p50().toString());
                metrics.put("targetDate", fc.targetDate() != null ? fc.targetDate().toString() : "N/A");
                metrics.put("remainingItems", fc.remainingItems());
                answer = String.format("Delivery forecast for **%s release %s**:\n- **Target Date:** %s\n- **Target Confidence:** **%d%%**\n- **85th Percentile (p85) Finish:** **%s**\n- **50th Percentile (p50) Finish:** **%s**\n- **95th Percentile (p95) Finish:** **%s**\n- **Remaining Items:** %d items (Monte Carlo 10k runs, capacity factor: %.1f%%)",
                        proj, release,
                        fc.targetDate() != null ? fc.targetDate() : "N/A",
                        confPct,
                        fc.p85(),
                        fc.p50(),
                        fc.p95(),
                        fc.remainingItems(),
                        fc.capacityFactor() * 100);
            } else {
                answer = "Unable to compute forecast for " + proj + " " + release;
            }
        }

        return ResponseEntity.ok(new ChatResponse(answer, toolCalled, result, metrics));
    }
}
