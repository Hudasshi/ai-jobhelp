package com.qznu.jobhelper.task;

import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 异步任务查询与通知已读接口。
 *
 * <p>任务正文只允许任务所属用户读取；Controller 不接收 userId，归属完全来自
 * 当前 JWT 上下文，防止前端伪造参数越权。</p>
 */
@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskId}")
    public Result<Map<String, Object>> get(@PathVariable Long taskId) {
        return Result.ok(taskService.getTask(UserContext.requireUserId(), taskId));
    }

    @PutMapping("/{taskId}/read")
    public Result<Void> markRead(@PathVariable Long taskId) {
        taskService.markRead(UserContext.requireUserId(), taskId);
        return Result.ok();
    }
}
