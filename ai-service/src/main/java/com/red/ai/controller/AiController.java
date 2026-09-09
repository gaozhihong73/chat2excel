package com.red.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.red.ai.dto.request.AiChatRequest;
import com.red.ai.dto.request.AiRequestHistoryRequest;
import com.red.ai.dto.request.SendEmailRequest;
import com.red.ai.dto.response.AiRequestHistoryResponse;
import com.red.ai.service.AiService;
import com.red.common.util.JwtUtil;
import com.red.common.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * AI服务控制器
 */
@RestController
@RequestMapping("/ai")
public class AiController {
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AiService aiService;

    /**
     * 处理聊天流式请求的端点方法
     * 使用Server-Sent Events (SSE)技术实现服务器向客户端推送实时数据流
     *
     * @param authorization 请求头中的授权信息，用于用户身份验证
     * @param request       包含聊天内容的请求体对象
     * @return SseEmitter 用于向客户端推送流式数据的SSE发射器
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUnifiedChat(
            @RequestHeader("Authorization") String authorization,  // 从请求头获取授权信息
            @RequestBody AiChatRequest request) {                    // 从请求体获取聊天请求内容
        // 通过JWT工具类解析授权信息，获取用户ID
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        // 创建SSE发射器，用于向客户端推送流式数据
        SseEmitter sseEmitter = new SseEmitter();


        // 验证用户身份
        if (userId == null) {  // 如果用户ID为空，说明令牌无效
            try {
                // 发送错误事件到客户端
                sseEmitter.send(SseEmitter.event().name("error").data("无效的令牌"));
                sseEmitter.complete();  // 完成发射器
                return sseEmitter;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // 在新线程中处理聊天请求，避免阻塞主线程
        new Thread(() -> {
            aiService.streamUnifiedChat(userId, request, sseEmitter);
        }).start();
        return sseEmitter;
    }

    /**
     * 获取AI请求历史记录接口
     *
     * @param authorization           请求头中的授权信息，用于用户身份验证
     * @param aiRequestHistoryRequest 请求体中的历史查询参数，包含分页、过滤等信息
     * @return 返回历史记录列表的响应结果
     */
    @GetMapping("/requests")
    public Result<IPage<AiRequestHistoryResponse>> getRequestsHistory(
            @RequestHeader("Authorization") String authorization,  // 从请求头获取授权信息
            @Validated AiRequestHistoryRequest aiRequestHistoryRequest) {                  // 从请求体获取聊天请求内容
        // 通过JWT解析用户ID
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        // 验证令牌有效性
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        // 调用服务层获取历史记录
        IPage<AiRequestHistoryResponse> response = aiService.getRequestsHistory(aiRequestHistoryRequest, userId);
        // 返回成功响应
        return Result.success("查询历史记录成功", response);
    }


    /**
     * 处理发送邮件的请求接口
     *
     * @param authorization 请求头中的Authorization字段，用于JWT认证
     * @param request       包含邮件发送请求体的参数
     * @return 返回操作结果，包含是否发送成功
     */
    @PostMapping("/send-email")
    public Result<Boolean> sendEmailWithExcel(
            @RequestHeader("Authorization") String authorization,
            @RequestBody @Validated SendEmailRequest request) {
        // 通过JWT解析用户ID，获取当前登录用户信息
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        // 验证令牌有效性，如果无效则返回错误信息
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }

        // 调用AI服务发送邮件，并返回操作结果
        return Result.success("发送邮件成功", aiService.sendEmailWithExcel(request));
    }
}
