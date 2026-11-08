package com.qznu.jobhelper.ability.controller;

import com.qznu.jobhelper.ability.dto.AbilityRequest;
import com.qznu.jobhelper.ability.dto.EnabledRequest;
import com.qznu.jobhelper.ability.service.AbilityService;
import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 能力库接口。
 */
@Validated
@RestController
@RequestMapping("/api/ability")
public class AbilityController {

    private final AbilityService abilityService;

    public AbilityController(AbilityService abilityService) {
        this.abilityService = abilityService;
    }

    @GetMapping("/field-spec")
    public Result<Map<String, Object>> fieldSpec() {
        return Result.ok(abilityService.fieldSpec());
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody AbilityRequest request) {
        return Result.ok(abilityService.create(UserContext.requireUserId(), request));
    }

    @PutMapping
    public Result<Map<String, Object>> update(@Valid @RequestBody AbilityRequest request) {
        return Result.ok(abilityService.update(UserContext.requireUserId(), request));
    }

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(abilityService.list(
                UserContext.requireUserId(), entryType, status, enabled, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(abilityService.get(UserContext.requireUserId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        abilityService.delete(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @PutMapping("/{id}/enabled")
    public Result<Void> enabled(@PathVariable Long id,
                                @Valid @RequestBody EnabledRequest request) {
        abilityService.setEnabled(UserContext.requireUserId(), id, request.enabled());
        return Result.ok();
    }

    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        abilityService.confirm(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        abilityService.reject(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
