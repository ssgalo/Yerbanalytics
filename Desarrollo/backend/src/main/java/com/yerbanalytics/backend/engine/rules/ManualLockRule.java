package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Guards against autonomous actuation when a manual override lock is active (HU-19).
 *
 * <p><b>Priority:</b> 0 — evaluated first. If a lock is active, no subsequent rule runs:
 * the operator has full control.
 *
 * <p><b>Condition:</b> {@link RuleContext#bloqueoManualActivo()} is {@code true}, meaning
 * a {@link ManualLockRepository} record with {@code active=true} exists for this sector
 * or its zone. The field is resolved by {@code NurseryService} before building the context.
 *
 * <p><b>Action when locked:</b> {@code ABORT_ALL} — stops the entire evaluation chain for
 * this sector in the current cycle.
 *
 * <p><b>Action when unlocked:</b> {@code NOOP_INFO} — confirms no manual intervention is
 * active; the chain continues.
 */
@Component
public class ManualLockRule implements Rule {

    private static final int PRIORITY = 0;
    private static final String NAME = "ManualLockRule";

    @SuppressWarnings("unused") // injected for future direct-query use in Watchdog mode
    private final ManualLockRepository manualLockRepository;

    public ManualLockRule(ManualLockRepository manualLockRepository) {
        this.manualLockRepository = manualLockRepository;
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
        if (ctx.bloqueoManualActivo()) {
            String reason = String.format(
                    "Manual override lock active on sector %s or its zone. " +
                    "All autonomous actuation suspended until the operator deactivates it.",
                    ctx.sector().getId());
            return List.of(RuleAction.of(ActionType.ABORT_ALL, NAME, reason));
        }

        return List.of(RuleAction.noopInfo(NAME,
                "No active manual lock — evaluation chain continues."));
    }
}
