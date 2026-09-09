package com.red.user.controller;

import com.red.common.annotation.LogOperation;
import com.red.common.util.JwtUtil;
import com.red.common.util.Result;
import com.red.user.dto.request.AuthRequest;
import com.red.user.dto.request.ChangePasswordRequest;
import com.red.user.dto.request.SendCodeRequest;
import com.red.user.dto.response.AuthResponse;
import com.red.user.dto.response.SendCodeResponse;
import com.red.user.dto.response.UserInfoResponse;
import com.red.user.service.UserService;
import com.red.user.service.VerificationCodeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/verification-code")
    @LogOperation("发送验证码")
    public Result<SendCodeResponse> sendVerificationCode(@RequestBody @Valid SendCodeRequest request) {
        int expireSeconds = verificationCodeService.sendCode(request.getEmail());
        String sendTo = maskEmail(request.getEmail());
        SendCodeResponse response = SendCodeResponse.builder().expireTime(expireSeconds).snedTo(sendTo).build();
        return Result.success("验证码发送成功", response);
    }

    @PostMapping("/auth")
    @LogOperation("统一认证注册与登录")
    public Result<AuthResponse> auth(@RequestBody @Valid AuthRequest authRequest) {
        AuthResponse authResponse = userService.auth(authRequest);
        String message = authResponse.isNewUser() ? "注册并登录成功" : "登录成功";
        return Result.success(message, authResponse);
    }

    @PostMapping("/logout")
    @LogOperation("用户登出")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = true) String authorization) {
        userService.logout(authorization);
        return Result.success("用户登出成功", null);
    }


    /**
     * 修改密码
     *
     * @param authorization 令牌
     * @return
     */
    @PostMapping("/change-password")
    @LogOperation("修改密码")
    public Result<UserInfoResponse> changePassword(@RequestHeader(value = "Authorization", required = true) String authorization,
                                                   @RequestBody @Valid ChangePasswordRequest request) {
        return Result.success("密码修改成功", userService.changePassword(authorization, request));
    }

    @GetMapping("/info")
    @LogOperation("用户信息查询")
    public Result<UserInfoResponse> getUserInfo(@RequestHeader(value = "Authorization", required = true) String authorization) {
        return Result.success("信息获取成功", userService.getUserInfo(authorization));
    }


    /**
     * 邮箱脱敏
     *
     * @param email 邮箱
     * @return 脱敏后的邮箱
     */
    private String maskEmail(String email) {
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.substring(0, 2) + "***@" + domain;
    }
}
