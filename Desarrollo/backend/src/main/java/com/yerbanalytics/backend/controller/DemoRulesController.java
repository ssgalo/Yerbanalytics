package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.DemoRuleDto;
import com.yerbanalytics.backend.dto.DemoRuleUpdateDto;
import com.yerbanalytics.backend.engine.overrides.ControllableState;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.DemoRuleControlService;
import com.yerbanalytics.backend.engine.overrides.InvalidOverrideKeyException;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.UnknownControllableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Demo-only REST API for runtime rule control.
 *
 * <p>Only registered when {@code yerbanalytics.demo.enabled=true}; in production the routes
 * do not exist (Spring never registers this bean and they return 404).
 *
 * <p>Covers every {@link DemoControllable} — per-sector rules, per-zone rules and services alike — not only the
 * per-sector chain. Writes go to the in-memory override store, never persisted: a restart resets every rule to its
 * production behaviour.
 */
@RestController
@RequestMapping("/api/demo/rules")
@ConditionalOnProperty(name = "yerbanalytics.demo.enabled", havingValue = "true")
public class DemoRulesController {

    private final DemoRuleControlService service;

    public DemoRulesController(DemoRuleControlService service) {
        this.service = service;
    }

    /** Lists every controllable piece with its current override state. */
    @GetMapping
    public List<DemoRuleDto> list() {
        return service.list().stream().map(DemoRulesController::toDto).toList();
    }

    /**
     * Updates the enabled flag, cooldown overrides and/or bypass set of a single piece. {@code 404} if it does not
     * exist; {@code 400} if a cooldown or bypass key is not one the piece declares.
     */
    @PutMapping("/{ruleName}")
    public DemoRuleDto update(@PathVariable String ruleName, @RequestBody DemoRuleUpdateDto body) {
        return toDto(service.update(ruleName, body.enabled(), body.cooldownMinutes(), body.bypassed()));
    }

    /** Shortcut: sets every declared cooldown of every piece to 0 (no wait between consecutive evaluations). */
    @PutMapping("/cooldowns/zero")
    public List<DemoRuleDto> zeroCooldowns() {
        return service.zeroCooldowns().stream().map(DemoRulesController::toDto).toList();
    }

    @ExceptionHandler(UnknownControllableException.class)
    public ResponseEntity<Map<String, String>> handleUnknown(UnknownControllableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidOverrideKeyException.class)
    public ResponseEntity<Map<String, String>> handleInvalidKey(InvalidOverrideKeyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    private static DemoRuleDto toDto(ControllableState state) {
        DemoControllable controllable = state.controllable();
        RuleOverride override = state.override();
        return new DemoRuleDto(
                controllable.name(),
                controllable.label(),
                controllable.branch().name(),
                controllable.priority(),
                !Boolean.FALSE.equals(override.enabled()),
                state.operationalActive(),
                state.effectiveEnabled(),
                controllable.cooldownKeys(),
                controllable.bypassKeys(),
                override.cooldownMinutes(),
                override.bypassed());
    }
}
