package com.aida.mcp;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** verify_answer. */
@Component
public class GuardrailTools implements AidaToolSet {

    private final ToolSupport support;
    private final Guardrail guardrail;

    public GuardrailTools(ToolSupport support, Guardrail guardrail) {
        this.support = support;
        this.guardrail = guardrail;
    }

    @Tool(name = Guardrail.TOOL, description = """
            Check a draft answer before sending it. Every number, percentage and date in the draft must come from \
            AIDA tool results you received in the last 30 minutes. Returns ok, and unmatched: the draft's text for \
            each value with no source. If ok is false, correct or remove each unmatched value (or call the AIDA \
            tool that provides it) and check again. Do not send numbers AIDA did not compute.""")
    public Map<String, Object> verifyAnswer(
            @ToolParam(description = "The full draft answer, exactly as it would be sent") String draft) {
        Map<String, Object> args = Map.of("draftChars", draft == null ? 0 : draft.length());
        return support.call(Guardrail.TOOL, null, args,
                () -> guardrail.verifyAnswer(ToolSupport.currentPrincipal().name(), draft));
    }
}
