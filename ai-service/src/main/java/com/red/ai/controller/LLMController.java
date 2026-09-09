package com.red.ai.controller;

import com.red.ai.dto.request.AiChatRequest;
import com.red.ai.dto.request.ProviderSwitchRequest;
import com.red.ai.service.AiService;
import com.red.ai.service.LLMService;
import com.red.common.util.JwtUtil;
import com.red.common.util.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 大模型管理的控制器
 */
@RestController
@RequestMapping("/llm")
public class LLMController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private LLMService llmService;

    // 获取所有可用的大模型列表
    @GetMapping("providers/list")
    public Result<List<Map<String, Object>>> getProviders(@RequestHeader("Authorization") String authorization) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("获取大模型提供商列表成功", llmService.getProvidersList());
    }

    // 切换大模型提供商
    @PostMapping("/providers/switch")
    public Result<Map<String, String>> switchProvider(
            @RequestHeader("Authorization") String authorization,
            @RequestBody @Valid ProviderSwitchRequest request) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("切换大模型提供商成功", llmService.switchProvider(request.getProviderName()));
    }


    // 获取大模型提供商
    @GetMapping("/providers/current")
    public Result<Map<String, String>> getCurrentProvider(
            @RequestHeader("Authorization") String authorization) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("获取大模型提供商信息成功", llmService.getCurrentProvider());
    }
}
