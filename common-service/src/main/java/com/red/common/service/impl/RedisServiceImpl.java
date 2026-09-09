package com.red.common.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red.common.service.RedisService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


/**
 * 缓存服务的实现类
 */

@Service
@Slf4j

public class RedisServiceImpl implements RedisService {
    /**
     * 验证码前缀
     */
    private static final String CODE_PREFIX = "code:";
    /**
     * 个人令牌前缀
     */
    private static final String TOKEN_PREFIX = "token:";
    /**
     * 老用户令牌
     */
    private static final String USER_SESSION_PREFIX = "user_session:";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void storeVerificationCode(String code, Long userId, String userName) {
        // 1. 生成key
        String key = CODE_PREFIX + code;
        CodeInfo codeInfo = new CodeInfo(userId, userName);
        // 2. 对象序列化之后，写入redis
        try {
            String value = objectMapper.writeValueAsString(codeInfo);
            stringRedisTemplate.opsForValue().set(key, value, 300, TimeUnit.SECONDS);
            log.info("[Redis存储成功] 验证码 {} 用户名 {}", code, userName);
        } catch (JsonProcessingException e) {
            log.error("[Redis存储失败] 验证码 {}  用户名 {}", code, userName);
        }
    }

    @Override
    public Long getUserIdByCode(String code) {
        // 1. 生成key
        String key = CODE_PREFIX + code;
        String value = stringRedisTemplate.opsForValue().get(key);

        if (value != null) {
            try {
                CodeInfo codeInfo = objectMapper.readValue(value, CodeInfo.class);
                return codeInfo.userId;
            } catch (JsonProcessingException e) {
                log.error("[Redis读取失败] 验证码 {}  报错 {}", code, e.getMessage());
            }
        }
        return null;
    }

    @Override
    public boolean isCodeValid(String code) {
        // 1. 生成key
        String key = CODE_PREFIX + code;
        return stringRedisTemplate.hasKey(key);
    }

    @Override
    public void removeCode(String code) {
        // 1. 生成key
        String key = CODE_PREFIX + code;
        stringRedisTemplate.delete(key);
        log.info("验证码已使用，现已废除该验证码。");
    }

    @Override
    public void storeToken(String token, Long userId, String username, Long expireSeconds) {
        String key = TOKEN_PREFIX + token;
        TokenInfo tokenInfo = new TokenInfo(userId, username);
        try {
            String value = objectMapper.writeValueAsString(tokenInfo);
            stringRedisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("[Redis 令牌存储失败 {} {}]", token, e.getMessage());
        }
    }

    @Override
    public void storeUserActivateToken(Long userId, String token, Long expireSeconds) {
        String key = USER_SESSION_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(key, token, expireSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getUserToken(Long userId) {
        String key = USER_SESSION_PREFIX + userId;
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean isUserLogged(Long userId) {
        String key = USER_SESSION_PREFIX + userId;
        return stringRedisTemplate.hasKey(key);
    }

    @Override
    public Long getUserIdByAuthorization(String authorization) {
        // 获取原生的Token
        String token = authorization.replaceFirst("(?i)^Bearer", "").trim();
        String key = TOKEN_PREFIX + token;
        String value = stringRedisTemplate.opsForValue().get(key);

        try {
            TokenInfo tokenInfo = objectMapper.readValue(value, TokenInfo.class);
            return tokenInfo.userId;
        } catch (JsonProcessingException e) {
            log.error("[getUserIdByAuthorization失败 {} {}]", token, e.getMessage());
        }
        return null;
    }

    @Override
    public void removeAuthorization(String authorization) {
        // 获取原生的Token
        String token = authorization.replaceFirst("(?i)^Bearer", "").trim();
        // 获取用户令牌缓存key
        String tokenKey = TOKEN_PREFIX + token;
        // 获取活跃用户缓存key
        Long userId = getUserIdByAuthorization(token);
        String userKay = USER_SESSION_PREFIX + userId;
        // 删除
        stringRedisTemplate.delete(tokenKey);
        log.info("用户令牌缓存已删除");
        stringRedisTemplate.delete(userKay);
        log.info("活跃用户缓存已删除");
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class CodeInfo {
        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 用户名：邮箱或者用户名
         */
        private String userName;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class TokenInfo {
        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 用户名：邮箱或者用户名
         */
        private String userName;
    }


}

