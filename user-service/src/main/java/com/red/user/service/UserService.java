package com.red.user.service;

import com.red.user.dto.request.AuthRequest;
import com.red.user.dto.request.ChangePasswordRequest;
import com.red.user.dto.response.AuthResponse;
import com.red.user.dto.response.UserInfoResponse;
import jakarta.validation.Valid;

/**
 * 用户服务相关接口
 */
public interface UserService {
    /**
     * 登陆注册
     * @param authRequest
     * @return
     */
    AuthResponse auth(AuthRequest authRequest);

    /**
     * 查询用户信息
     * @param authorization
     * @return
     */
    UserInfoResponse getUserInfo(String authorization);

    /**
     * 修改密码
     * @param authorization 令牌
     * @return
     */
    UserInfoResponse changePassword(String authorization, ChangePasswordRequest request);

    /**
     * 登出
     * @param authorization token
     */
    void logout(String authorization);
}
