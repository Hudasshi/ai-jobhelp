package com.qznu.jobhelper.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一返回结果封装类
 * 所有接口响应统一使用该对象包装，规范前端返回格式
 * @author HU
 * @version 1.0
 * @date 2026/11/7 17:19
 * @description 接口统一响应实体
 */
public final class Result<T> {

    /**
     * 响应状态码：0代表成功，非0代表失败
     */
    private final int code;
    /**
     * 响应提示信息，给前端展示的简短文案
     */
    private final String msg;
    /**
     * 返回业务数据，泛型适配不同返回类型
     */
    private final T data;
    /**
     * 详细原因，用于后端排查问题，不展示给普通用户
     */
    private final String reason;

    /**
     * 私有构造方法，禁止外部new，只能通过静态方法构建Result
     * @param code 状态码
     * @param msg 提示信息
     * @param data 业务数据
     * @param reason 详细异常原因
     */
    private Result(int code, String msg, T data, String reason) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.reason = reason;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    /**
     * 数据字段：无论是否为null都序列化返回
     * @return 业务数据
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public T getData() {
        return data;
    }

    /**
     * 详细原因字段：值为null时不参与json序列化，减少返回字段
     * @return 详细原因
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getReason() {
        return reason;
    }

    /**
     * 成功响应，自定义返回数据，默认提示“成功”
     * @param data 返回业务数据
     * @return Result
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "成功", data, null);
    }

    /**
     * 无业务数据的成功响应，例如修改密码、删除记录。
     */
    public static Result<Void> ok() {
        return new Result<>(0, "成功", null, null);
    }

    /**
     * 成功响应，自定义提示信息和返回数据
     * @param msg 自定义成功提示
     * @param data 返回业务数据
     * @return Result
     */
    public static <T> Result<T> ok(String msg, T data) {
        return new Result<>(0, msg, data, null);
    }

    /**
     * 失败响应，自定义状态码和提示信息，无返回数据
     * @param code 错误码
     * @param msg 错误提示
     * @return Result
     */
    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null, null);
    }

    /**
     * 带诊断原因的业务失败响应。
     * reason 只用于 403 等需要前端区分处理策略的场景，普通业务失败不应暴露内部细节。
     */
    public static <T> Result<T> fail(int code, String msg, String reason) {
        return new Result<>(code, msg, null, reason);
    }

    /**
     * 权限不足403响应
     * @param msg 给用户看的提示
     * @param reason 权限不足详细原因
     * @return Result
     */
    public static <T> Result<T> forbidden(String msg, String reason) {
        return new Result<>(403, msg, null, reason);
    }
}
