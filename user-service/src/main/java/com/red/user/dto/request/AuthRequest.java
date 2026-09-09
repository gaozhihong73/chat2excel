package com.red.user.dto.request;

import lombok.Data;

/**
 * 统一认证请求参数（注册 + 登录）
 */
@Data
public class AuthRequest {
    private String username;
    private String password;
    private  String email;
    /**
     * 验证码
     */
    private String verificationCode;
}
