package com.red.user.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 查询用户信息响应
 */
@Data
@Builder
public class UserInfoResponse {
    private Long userId;
    private String username;
    private String email;
}
