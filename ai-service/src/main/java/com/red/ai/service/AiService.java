package com.red.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.red.ai.dto.request.AiChatRequest;
import com.red.ai.dto.request.AiRequestHistoryRequest;
import com.red.ai.dto.request.SendEmailRequest;
import com.red.ai.dto.response.AiRequestHistoryResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiService {
    /**
     * 统一聊天流式响应方法
     *
     * @param userId     用户ID，用于标识请求的用户
     * @param request    AI聊天请求对象，包含聊天内容等相关信息
     * @param sseEmitter 服务器发送事件(SSE)发射器，用于向客户端推送流式数据
     */
    void streamUnifiedChat(Long userId, AiChatRequest request, SseEmitter sseEmitter);

    /**
     * 获取AI请求历史记录
     *
     * @param aiRequestHistoryRequest 包含请求历史查询条件的请求对象
     * @param userId 用户ID，用于筛选特定用户的请求历史
     * @return 返回一个分页结果，包含AI请求历史记录的响应列表
     */
    IPage<AiRequestHistoryResponse> getRequestsHistory(AiRequestHistoryRequest aiRequestHistoryRequest, Long userId);

/**
 * 发送包含Excel附件的邮件方法
 *
 * @param request 发送邮件的请求参数对象，包含邮件收件人、主题、内容以及Excel附件等信息
 * @return 发送结果，Boolean类型表示邮件是否发送成功
 */
    Boolean sendEmailWithExcel(SendEmailRequest request);
}
