package com.qznu.jobhelper.delivery.controller;

import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.UserContext;
import com.qznu.jobhelper.delivery.dto.CreateDeliveryRequest;
import com.qznu.jobhelper.delivery.dto.DeliveryStatusRequest;
import com.qznu.jobhelper.delivery.dto.ParseJobDescriptionRequest;
import com.qznu.jobhelper.delivery.dto.UpdateDeliveryRequest;
import com.qznu.jobhelper.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户投递看板接口。
 *
 * <p>所有请求的归属用户都从 JWT 上下文读取，客户端不能通过 userId 参数选择其他
 * 用户的数据。</p>
 */
@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list() {
        return Result.ok(deliveryService.list(UserContext.requireUserId()));
    }

    @GetMapping("/todo")
    public Result<List<Map<String, Object>>> todo() {
        return Result.ok(deliveryService.todo(UserContext.requireUserId()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(deliveryService.get(UserContext.requireUserId(), id));
    }

    @PostMapping
    public Result<Map<String, Object>> create(
            @Valid @RequestBody CreateDeliveryRequest request) {
        return Result.ok(deliveryService.create(UserContext.requireUserId(), request));
    }

    @PutMapping
    public Result<Map<String, Object>> update(
            @Valid @RequestBody UpdateDeliveryRequest request) {
        return Result.ok(deliveryService.update(UserContext.requireUserId(), request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deliveryService.delete(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @PostMapping("/parse-jd")
    public Result<Map<String, Object>> parseJd(
            @Valid @RequestBody ParseJobDescriptionRequest request) {
        return Result.ok(deliveryService.parseJobDescription(
                UserContext.requireUserId(), request));
    }

    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody DeliveryStatusRequest request) {
        deliveryService.changeStatus(UserContext.requireUserId(), id, request);
        return Result.ok();
    }
}
