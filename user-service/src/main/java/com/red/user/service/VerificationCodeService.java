package com.red.user.service;

/**
 * 验证码相关接口
 */
public interface VerificationCodeService {
    /**
     * 发送验证码
     * @param email
     * @return 过期时长 秒
     */
    int sendCode(String email);


    /**
     * 校验验证码
     * @param email
     * @param code
     * @return 是否校验通过
     */
    boolean verifyCode(String email, String code);

    /**
     * 根据验证码获取用户的ID
     * @param code 验证码
     * @return 用户ID
     */
    Long getUserId(String code);


    /**
     * 销毁验证码
     * @param code
     */
    void removeCode(String code);
}
