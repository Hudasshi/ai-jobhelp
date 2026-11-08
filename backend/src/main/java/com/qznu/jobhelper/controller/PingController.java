package com.qznu.jobhelper.controller;

import com.qznu.jobhelper.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author HU
 * @version 1.0
 * @date 2026/11/7 19:39
 * @description
 */
@RestController
public class PingController {
    @GetMapping("/ping")
    public Result<Void> ping() {
        return Result.ok("ok", null);
    }
}
