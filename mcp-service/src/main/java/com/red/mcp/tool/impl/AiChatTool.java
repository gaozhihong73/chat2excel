package com.red.mcp.tool.impl;

import com.red.mcp.model.McpToolResult;
import com.red.mcp.tool.McpToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AiChatTool implements McpToolExecutor {
    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${mcp.services.gateway-url}")
    private String gatewayUrl;

    @Override
    public String getToolName() {
        return "ai_chat";
    }

    @Override
    public String getToolDesc() {
        return "AI智能对话工具";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "userInput", Map.of(
                        "type", "string",
                        "description", "用户消息内容"
                ),
                "fileId", Map.of(
                        "type", "integer",
                        "description", "要分析的excel文件ID"
                ),
                "token", Map.of(
                        "type", "string",
                        "description", "用户的个人认证令牌"
                )
        ));
        schema.put("required", Arrays.asList("message", "fileId", "token"));
        return schema;
    }

    /**
     * 执行Mcp工具的方法
     *
     * @param arguments 调用参数，为必填项，不能为null
     * @return 返回执行结果
     */
    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        // 1. 提取参数
        String token = (String) arguments.get("token");
        Long fileId = Long.valueOf(arguments.get("fileId").toString());  // 文件ID
        String userInput = (String) arguments.get("message");  // 用户的输入


        // 2. 发起请求
        StringBuilder url = new StringBuilder(gatewayUrl + "/api/v1/ai/chat/stream");
        WebClient webClient = webClientBuilder != null ?
                webClientBuilder.build() : WebClient.builder().build();
        String bearer = token.startsWith("Bearer ") ? token : "Bearer " + token;
        // 3. 构建请求体
        Map<String, Object> requestBody = Map.of(
                "fileId", fileId,
                "userInput", userInput
        );

        // 3. 判断响应结果
        try {
            String response = webClient.post()
                    .uri(url.toString())
                    .headers(h -> {
                        h.add("Authorization", bearer);
                        h.add("Accept", "text/event-stream");
                    })
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();
            // 4. 封装返回对象
            return McpToolResult.builder()
                    .toolName(getToolName())
                    .content(response)
                    .success(true)
                    .build();
        } catch (Exception e) {
            return McpToolResult.builder()
                    .toolName(getToolName())
                    .content("")
                    .success(false)
                    .error("调用工具失败：" + e.getMessage())
                    .build();
        }
    }
}
