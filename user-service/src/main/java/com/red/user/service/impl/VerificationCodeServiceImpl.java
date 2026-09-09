package com.red.user.service.impl;

import com.red.common.service.EmailService;
import com.red.common.service.RedisService;
import com.red.user.entity.UserEntity;
import com.red.user.mapper.UserMapper;
import com.red.user.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 验证码校验服务实现类
 */
@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisService redisService;

    @Override
    public int sendCode(String email) {
        // 1. 生成验证码
        String code = String.valueOf(new Random().nextInt(100000,1000000));

        // 2. 发送验证码
        boolean ifSendSuccess = emailService.sendVerificationCode(email, code);

        // 3. 查询数据框的判断逻辑（根据邮箱去查询user表，存在即为登录，不存在即为注册）
        UserEntity user = userMapper.findByLoginKey(email);

        if (user == null) { // 若为空，说明是新注册的用户
            user = new UserEntity();
            user.setId(0L);
            user.setUsername(email);
        } else {  // 若不为空，说明是登录的用户

        }

        // 验证码信息写入Redis缓存
        redisService.storeVerificationCode(code, user.getId(), user.getUsername());

        if (ifSendSuccess) {
            log.info("[验证码发送成功], 验证码{} 已经发送至{}", code, email);
            return 300;
        } else {
            log.error("[验证码发送失败], 验证码{} 没有发送至{}", code, email);
            return -1;
        }
    }

    @Override
    public boolean verifyCode(String email, String code) {
        return redisService.isCodeValid(code);
    }

    @Override
    public Long getUserId(String code) {
        return redisService.getUserIdByCode(code);
    }

    @Override
    public void removeCode(String code) {
        redisService.removeCode(code);
    }
}
