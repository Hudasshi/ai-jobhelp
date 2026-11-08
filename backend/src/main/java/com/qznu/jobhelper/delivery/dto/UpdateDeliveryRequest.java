package com.qznu.jobhelper.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 编辑投递请求。接口契约规定 id 放在请求体中，因为编辑路径固定为 PUT /api/delivery。
 *
 * <p>这里刻意不使用 record：契约要求区分“status 字段未出现”和“status 字段出现但值为
 * null”。record 构造器无法保留属性是否存在，因此用普通 DTO 在 setter 中记录
 * statusPresent。</p>
 */
public class UpdateDeliveryRequest {

    @NotNull(message = "投递记录ID不能为空")
    public Long id;

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 100, message = "公司名称不能超过100字")
    public String company;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 100, message = "岗位名称不能超过100字")
    public String position;

    @Size(max = 8000, message = "JD不能超过8000字")
    public String jdRaw;

    @NotNull(message = "投递日期不能为空")
    public LocalDate deliveryDate;

    public String status;
    public LocalDateTime examTime;
    public LocalDateTime interviewTime;

    @Size(max = 500, message = "技能要求不能超过500字")
    public String skillReq;

    public LocalDate deadline;

    @Size(max = 500, message = "备注不能超过500字")
    public String remark;

    @JsonIgnore
    private boolean statusPresent;

    @JsonSetter("status")
    public void setStatus(String status) {
        this.status = status;
        this.statusPresent = true;
    }

    // 保留 record 风格访问器，Service 层无需为了 DTO 改造无条件扩散。
    public Long id() {
        return id;
    }

    public String company() {
        return company;
    }

    public String position() {
        return position;
    }

    public String jdRaw() {
        return jdRaw;
    }

    public LocalDate deliveryDate() {
        return deliveryDate;
    }

    public String status() {
        return status;
    }

    public LocalDateTime examTime() {
        return examTime;
    }

    public LocalDateTime interviewTime() {
        return interviewTime;
    }

    public String skillReq() {
        return skillReq;
    }

    public LocalDate deadline() {
        return deadline;
    }

    public String remark() {
        return remark;
    }

    public boolean statusPresent() {
        return statusPresent;
    }
}
