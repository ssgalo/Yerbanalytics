package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.service.HistorialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Post-action follow-up rule (R4/HU-12).
 *
 * <p><b>Priority:</b> 20 — the last rule evaluated. Unlike other rules, it does not decide
 * whether to act: its function is to verify the effectiveness of previous actions on the
 * sector and update the history accordingly.
 *
 * <p><b>Design:</b> this rule is a <em>delegator</em> — it does not duplicate the
 * effectiveness evaluation logic already present in
 * {@link HistorialService#evaluarSeguimiento()}. It simply invokes that method within the
 * rules engine context to centralize post-action evaluation. The global evaluation
 * (all sectors) continues to be triggered from the {@code @Scheduled} in
 * {@link HistorialService}.
 *
 * <p><b>Note on pattern:</b> the {@code @Scheduled} in {@link HistorialService} is NOT
 * removed — it is the watchdog covering sectors without recent telemetry. This rule
 * complements that scheduler by evaluating pending events in each reactive cycle.
 *
 * <p><b>Action:</b> always returns {@code NOOP_INFO} — follow-up is an internal audit
 * task and produces no actuator effects.
 */
@Component
public class FollowUpRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(FollowUpRule.class);
    private static final int PRIORITY = 20;
    private static final String NAME = "FollowUpRule";

    private final HistorialService historialService;

    public FollowUpRule(HistorialService historialService) {
        this.historialService = historialService;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx) {
        try {
            historialService.evaluarSeguimiento();
            log.debug("FollowUpRule: follow-up evaluation completed for sector {}.", ctx.sector().getId());
        } catch (Exception e) {
            log.warn("FollowUpRule: error during follow-up evaluation: {}", e.getMessage());
        }

        return List.of(RuleAction.noopInfo(NAME,
                "Evaluación de seguimiento post-acción completada."));
    }
}
