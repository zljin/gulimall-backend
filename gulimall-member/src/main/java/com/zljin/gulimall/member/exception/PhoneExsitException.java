package com.zljin.gulimall.member.exception;

public class PhoneExsitException extends RuntimeException {

    public PhoneExsitException() {
        super("手机号存在");
    }
}
