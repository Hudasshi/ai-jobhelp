package com.qznu.jobhelper.support.quota;

import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户配额查询。
 */
@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/remaining")
    public Result<Map<String, Object>> remaining() {
        QuotaService.QuotaSnapshot snapshot = quotaService.remaining(UserContext.requireUserId());
        return Result.ok(Map.of(
                "quotaUsed", snapshot.quotaUsed(),
                "quotaLimit", snapshot.quotaLimit(),
                "remaining", snapshot.remaining()
        ));
    }
}
