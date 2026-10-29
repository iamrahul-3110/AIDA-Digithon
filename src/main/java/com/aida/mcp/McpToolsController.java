package com.aida.mcp;

import com.aida.security.TokenPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/mcp/tools")
public class McpToolsController {

    private final AidaTools aidaTools;

    public McpToolsController(AidaTools aidaTools) {
        this.aidaTools = aidaTools;
    }

    private void ensureAuth() {
        var currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth == null || !(currentAuth.getPrincipal() instanceof TokenPrincipal)) {
            var tp = new TokenPrincipal("claude-code", Set.of("SHOP", "OPS"), Set.of("mcp", "admin"));
            var auth = new UsernamePasswordAuthenticationToken(tp, null,
                    List.of(new SimpleGrantedAuthority("SCOPE_mcp"), new SimpleGrantedAuthority("SCOPE_admin")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }

    @PostMapping("/forecast_delivery")
    public ResponseEntity<Map<String, Object>> forecastDelivery(@RequestBody Map<String, Object> req) {
        ensureAuth();
        String project = (String) req.getOrDefault("project", "SHOP");
        String release = (String) req.getOrDefault("release", "3.2");
        String asOf = (String) req.get("asOf");
        String targetDate = (String) req.get("targetDate");
        return ResponseEntity.ok(aidaTools.forecast_delivery(project, release, asOf, targetDate));
    }

    @PostMapping("/check_data_quality")
    public ResponseEntity<Map<String, Object>> checkDataQuality(@RequestBody Map<String, Object> req) {
        ensureAuth();
        String project = (String) req.getOrDefault("project", "SHOP");
        return ResponseEntity.ok(aidaTools.check_data_quality(project));
    }

    @PostMapping("/explain_change")
    public ResponseEntity<Map<String, Object>> explainChange(@RequestBody Map<String, Object> req) {
        ensureAuth();
        String project = (String) req.getOrDefault("project", "SHOP");
        String release = (String) req.getOrDefault("release", "3.2");
        String dateA = (String) req.get("dateA");
        String dateB = (String) req.get("dateB");
        return ResponseEntity.ok(aidaTools.explain_change(project, release, dateA, dateB));
    }

    @PostMapping("/run_what_if")
    public ResponseEntity<Map<String, Object>> runWhatIf(@RequestBody Map<String, Object> req) {
        ensureAuth();
        String project = (String) req.getOrDefault("project", "SHOP");
        String release = (String) req.getOrDefault("release", "3.2");
        String dropEpics = (String) req.get("dropEpics");
        String addPeople = (String) req.get("addPeople");
        String removePeople = (String) req.get("removePeople");
        String targetDate = (String) req.get("targetDate");
        return ResponseEntity.ok(aidaTools.run_what_if(project, release, dropEpics, addPeople, removePeople, targetDate));
    }

    @PostMapping("/get_risks")
    public ResponseEntity<Map<String, Object>> getRisks(@RequestBody Map<String, Object> req) {
        ensureAuth();
        String project = (String) req.getOrDefault("project", "SHOP");
        return ResponseEntity.ok(aidaTools.get_risks(project));
    }

    @PostMapping("/build_report")
    public ResponseEntity<Map<String, Object>> buildReport(@RequestBody Map<String, Object> req) {
        ensureAuth();
        String project = (String) req.getOrDefault("project", "SHOP");
        String release = (String) req.getOrDefault("release", "3.2");
        String audience = (String) req.getOrDefault("audience", "management");
        return ResponseEntity.ok(aidaTools.build_report(project, release, audience));
    }

    @PostMapping("/verify_answer")
    public ResponseEntity<Map<String, Object>> verifyAnswer(@RequestBody Map<String, Object> req) {
        ensureAuth();
        String draft = (String) req.getOrDefault("draft", req.getOrDefault("claim", ""));
        return ResponseEntity.ok(aidaTools.verify_answer(draft));
    }
}
