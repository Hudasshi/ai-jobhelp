package com.qznu.jobhelper.interview.controller;

import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.UserContext;
import com.qznu.jobhelper.interview.dto.InterviewAnswerRequest;
import com.qznu.jobhelper.interview.dto.StartInterviewRequest;
import com.qznu.jobhelper.interview.service.InterviewService;
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

/** 模拟面试启动、作答、复盘与归档接口。 */
@Validated
@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/start")
    public Result<Map<String, Object>> start(
            @Valid @RequestBody StartInterviewRequest request) {
        return Result.ok(interviewService.start(UserContext.requireUserId(), request));
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(interviewService.list(
                UserContext.requireUserId(), page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(interviewService.get(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/answer")
    public Result<Map<String, Object>> answer(@PathVariable Long id,
                                              @Valid @RequestBody InterviewAnswerRequest request) {
        return Result.ok(interviewService.answer(
                UserContext.requireUserId(), id, request));
    }

    @PostMapping("/{id}/review")
    public Result<Map<String, Object>> review(@PathVariable Long id) {
        return Result.ok(interviewService.review(UserContext.requireUserId(), id));
    }

    @PutMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        interviewService.archive(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
