package com.red.common.util;

import com.red.common.service.RedisService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Jwt工具类 负责生成、校验、管理用户登录令牌
 */
@Component
public class JwtUtil {

    @Autowired
    private RedisService redisService;

    @Value("72000")
    private Long exprieTime;

    /**
     * 签名秘钥对象
     */
    private SecretKey key;

    /**
     * H256秘钥
     */
    @Value("changeit-change-it-change-it-change-it-change-it")
    private String secret;

    /**
     * 初始化秘钥对象
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成JWT令牌并写入缓存
     *
     * @param userId   用户ID
     * @param username 用户名或邮箱
     * @return
     */
    public String createToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + exprieTime);
        String token = Jwts.builder()
                // 往 Token 内部塞入主体信息（通常是用户的 ID 或账号名）
                .setSubject(String.valueOf(userId)).claim("username", username)
                // 记录 Token 的签发时间
                .setIssuedAt(now)
                // 设定 Token 什么时候过期失效
                .setExpiration(expiry)
                // 【核心关键步】：使用你刚刚初始化的 this.key 对这个 Token 进行数字签名
                // 这一步是为了防止黑客拿到 Token 后偷偷修改里面的用户名
                .signWith(this.key, SignatureAlgorithm.HS256)
                // 最终打包压缩成一串类似 "eyJh..." 的 Base64 字符串返回给前端
                .compact();

        // token 存储到 redis
        redisService.storeToken(token, userId, username, exprieTime);
        // 存储活跃用户（避免重复登录）
        redisService.storeUserActivateToken(userId, token, exprieTime);
        return token;
    }

    /**
     * 获取当前用户的 token
     *
     * @param userId
     * @return
     */
    public String getUserActiveToken(Long userId) {
        return redisService.getUserToken(userId);
    }

    /**
     * 检查用户是否已登录
     *
     * @param userId
     * @return
     */
    public boolean isUserLogger(Long userId) {
        return redisService.isUserLogged(userId);
    }

    /**
     * 通过令牌获取用户ID
     * @param authorization
     * @return
     */
    public Long getUserIdByAuthorization(String authorization){
        // 从 Redis 中获取
        return redisService.getUserIdByAuthorization(authorization);
    }


    /**
     * 删除token
     * @param authorization
     * @return
     */
    public void removeAuthorization(String authorization) {
        redisService.removeAuthorization(authorization);
    }
}
