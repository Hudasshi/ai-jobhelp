package com.qznu.jobhelper.diagnosis.controller;

import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.UserContext;
import com.qznu.jobhelper.diagnosis.dto.AdoptRequest;
import com.qznu.jobhelper.diagnosis.dto.CreateDiagnosisRequest;
import com.qznu.jobhelper.diagnosis.service.DiagnosisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 简历诊断接口，异步任务由 GET /api/task/{taskId} 轮询。 */
@Validated
@RestController
@RequestMapping("/api/diagnose")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(
            @Valid @RequestBody CreateDiagnosisRequest request) {
        return Result.ok(diagnosisService.create(UserContext.requireUserId(), request));
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(diagnosisService.page(
                UserContext.requireUserId(), status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(diagnosisService.get(UserContext.requireUserId(), id));
    }

    @PutMapping("/{id}/items/{entryId}/adopt")
    public Result<Map<String, Object>> adopt(@PathVariable Long id,
                                             @PathVariable Long entryId,
                                             @Valid @RequestBody AdoptRequest request) {
        return Result.ok(diagnosisService.adopt(
                UserContext.requireUserId(), id, entryId, request));
    }
}
