package com.red.common.service;

/**
 * 缓存服务
 */
public interface RedisService {
    /**
     * 根据验证码信息写缓存
     *
     * @param code     验证码
     * @param userId   用户ID
     * @param userName 用户名
     */
    void storeVerificationCode(String code, Long userId, String userName);

    /**
     * 根据验证码获取用户ID信息
     *
     * @param code 验证码
     * @return 用户ID
     */
    Long getUserIdByCode(String code);

    /**
     * 校验验证码是否有效
     *
     * @param code 验证码
     * @return 是否有效
     */
    boolean isCodeValid(String code);

    /**
     * 删除验证码
     *
     * @param code
     */
    void removeCode(String code);


    /**
     * 把生成好的token存储到redis
     *
     * @param token         令牌
     * @param userId        用户ID
     * @param username      用户名
     * @param expireSeconds 过期时间 秒
     */
    void storeToken(String token, Long userId, String username, Long expireSeconds);

    /**
     * 用于处理重复登录 - 将token和用户id绑定存储到redis
     * @param userId
     * @param token
     * @param expireSeconds
     */
    void storeUserActivateToken(Long userId, String token, Long expireSeconds);

    /**
     * 获取用户token
     *
     * @param userId
     * @return 用户token
     */
    String getUserToken(Long userId);

    /**
     * 判断用户是否登录
     * @param userId
     * @return
     */
    boolean isUserLogged(Long userId);

    /**
     * 根据令牌来获取用户ID
     * @param authorization
     */
    Long getUserIdByAuthorization(String authorization);

    /**
     * 删除令牌
     * @param authorization
     */
    void removeAuthorization(String authorization);
}
