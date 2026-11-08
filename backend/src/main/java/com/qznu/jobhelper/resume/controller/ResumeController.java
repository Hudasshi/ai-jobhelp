package com.qznu.jobhelper.resume.controller;

import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.UserContext;
import com.qznu.jobhelper.resume.dto.GenerateResumeRequest;
import com.qznu.jobhelper.resume.dto.ResumeSaveRequest;
import com.qznu.jobhelper.resume.service.ResumeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 简历上传、生成和版本接口。
 */
@Validated
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/parse")
    public Result<Map<String, Object>> parse(@RequestPart("file") MultipartFile file) {
        return Result.ok(resumeService.parseUpload(UserContext.requireUserId(), file));
    }

    @PostMapping
    public Result<Map<String, Object>> save(@Valid @RequestBody ResumeSaveRequest request) {
        return Result.ok(resumeService.saveVersion(UserContext.requireUserId(), request));
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(resumeService.list(UserContext.requireUserId(), page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(resumeService.get(UserContext.requireUserId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resumeService.delete(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@Valid @RequestBody GenerateResumeRequest request) {
        return Result.ok(resumeService.generateDraft(UserContext.requireUserId(), request));
    }
}
