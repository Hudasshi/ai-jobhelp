package com.qznu.jobhelper.common.exception;

public class AuthException extends RuntimeException {

    public AuthException() {
        super("请先登录");
    }
}
