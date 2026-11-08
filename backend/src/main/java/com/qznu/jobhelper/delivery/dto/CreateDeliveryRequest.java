package com.qznu.jobhelper.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 新增投递请求。
 *
 * <p>公司、岗位与投递日期是投递事实的最小组成部分，因此必须存在。JD 可以为空，
 * 此时用户只维护台账；状态为空时由服务端补为 pending，避免前端把“未选择”误当成
 * 非法状态。</p>
 */
public record CreateDeliveryRequest(
        @NotBlank(message = "公司名称不能为空")
        @Size(max = 100, message = "公司名称不能超过100字")
        String company,

        @NotBlank(message = "岗位名称不能为空")
        @Size(max = 100, message = "岗位名称不能超过100字")
        String position,

        @Size(max = 8000, message = "JD不能超过8000字")
        String jdRaw,

        @NotNull(message = "投递日期不能为空")
        LocalDate deliveryDate,

        String status,
        LocalDateTime examTime,
        LocalDateTime interviewTime,

        @Size(max = 500, message = "技能要求不能超过500字")
        String skillReq,

        LocalDate deadline,

        @Size(max = 500, message = "备注不能超过500字")
        String remark
) {
}
