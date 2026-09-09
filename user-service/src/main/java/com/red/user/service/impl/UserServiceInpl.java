package com.red.user.service.impl;

import com.red.common.util.JwtUtil;
import com.red.user.dto.request.AuthRequest;
import com.red.user.dto.request.ChangePasswordRequest;
import com.red.user.dto.response.AuthResponse;
import com.red.user.dto.response.UserInfoResponse;
import com.red.user.entity.UserEntity;
import com.red.user.mapper.UserMapper;
import com.red.user.service.UserService;
import com.red.user.service.VerificationCodeService;
import io.micrometer.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceInpl implements UserService {

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse auth(AuthRequest authRequest) {
        // 1. 先检测当前是哪种登录方式
        boolean isEmailCodeMode = StringUtils.isNotBlank(authRequest.getEmail()) && StringUtils.isNotBlank(authRequest.getVerificationCode());
        boolean isPasswordMode = StringUtils.isNotBlank(authRequest.getUsername()) && StringUtils.isNotBlank(authRequest.getPassword());
        if (!isEmailCodeMode && !isPasswordMode) {
            throw new IllegalArgumentException("请提供有效的验证方式：邮箱 + 验证码 / 账号 + 密码");
        }

        UserEntity user = null;
        Boolean isNewUser = false;

        // 2. 先处理 邮箱 + 验证码的方式
        if (isEmailCodeMode) {
            if (!verificationCodeService.verifyCode(authRequest.getEmail(), authRequest.getVerificationCode())) {
                throw new IllegalArgumentException("验证码无效或过期");
            }
            // 判断注册还是登录
            Long userId = verificationCodeService.getUserId(authRequest.getVerificationCode());
            if (userId == 0L) { // 新用户注册
                user = new UserEntity();
                user.setEmail(authRequest.getEmail());
                user.setUsername(createRandomName());
                userMapper.insert(user);
                isNewUser = true;
            }
            // 删除验证码避免重复使用
            verificationCodeService.removeCode(authRequest.getVerificationCode());
        }

        // 3. 账号密码的方式
        if (isPasswordMode) {
            user = userMapper.findByLoginKey(authRequest.getUsername());
            if (user == null) { // 新用户、先注册
                user = new UserEntity();
                user.setUsername(createRandomName());
                user.setPasswordHash(passwordEncoder.encode(authRequest.getPassword()));
                userMapper.insert(user);
                isNewUser = true;
            } else { // 老用户校验密码
                if (!passwordEncoder.matches(authRequest.getPassword(), user.getPasswordHash())) {
                    throw new IllegalArgumentException("用户名与密码不匹配");
                }
            }
        }


        // 处理token
        String token;
        if (jwtUtil.isUserLogger(user.getId())) {
            // 若用户已登录，直接获取token
            token = jwtUtil.getUserActiveToken(user.getId());
        } else {
            // 若用户未登录，直接创建token
            token = jwtUtil.createToken(user.getId(), user.getUsername());
        }

        // 设置过期时间
        LocalDateTime expireTime = LocalDateTime.now().plusHours(20);
        String tokenExpireTime = expireTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 返回响应对象
        return AuthResponse.builder()
                .userId(user.getId())
                .userName(user.getUsername())
                .email(user.getEmail())
                .token(token)
                .tokenExpireTime(tokenExpireTime)
                .isNewUser(isNewUser)
                .build();
    }

    @Override
    public UserInfoResponse getUserInfo(String authorization) {
        // 1. 查缓存，得到userId
        // 查询缓存
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            throw new IllegalArgumentException("无效的令牌");
        }

        // 2. 查数据框，得到userInfo
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        return UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoResponse changePassword(String authorization, ChangePasswordRequest request) {
        // 新密码与确认密码必须一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }

        // 新旧密码不能相同
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new IllegalArgumentException("新旧密码不能相同");
        }

        // 查询令牌，确认用户
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            throw new IllegalArgumentException("无效的令牌");
        }
        // 查数据库，得到userInfo
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        } else if (user.getPasswordHash().isEmpty() || user.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("必须是使用用户名和密码登录的用户才可以修改密码");
        } else if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("原密码输入错误");
        }

        // 设置新密码
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // 更新数据库数据
        userMapper.updateById(user);

        return UserInfoResponse.builder().
                username(user.getUsername()).
                userId(user.getId()).
                build();
    }

    @Override
    public void logout(String authorization) {
        // 查询令牌，确认用户
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            throw new IllegalArgumentException("无效的令牌");
        }

        // 删除redis中的缓存
        jwtUtil.removeAuthorization(authorization);
    }

    /**
     * 生成全局唯一的用户名
     *
     * @return
     */
    private String createRandomName() {
        String username = "red_" + new Random().nextInt(10000000);
        // 1. 用户名要全局唯一
        if (userMapper.existByUserName(username) == 0) return username;
        else return "red_" + System.currentTimeMillis();
    }
}
