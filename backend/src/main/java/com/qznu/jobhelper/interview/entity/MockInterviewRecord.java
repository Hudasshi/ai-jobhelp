package com.qznu.jobhelper.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模拟面试记录。
 *
 * <p>questionsJson/answersJson/feedbackJson 分开保存，便于断点续答；
 * requestLogJson 用来实现同一 requestId 的点评幂等回放。</p>
 */
@Getter
@Setter
@TableName("mock_interview_record")
public class MockInterviewRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long deliveryId;
    private Long jobTargetId;
    private String questionsJson;
    private String answersJson;
    private String feedbackJson;
    private String requestLogJson;
    private Integer progressIndex;
    private String reviewText;
    private Integer archived;
    private LocalDateTime archiveTime;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
