package com.red.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red.ai.config.LLMConfig;
import com.red.ai.service.AbstractLLMProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek提供商实现类
 */
@Service
@Slf4j
public class DeepSeekProvider extends AbstractLLMProvider {

    @Autowired
    @Qualifier("deepSeekLLMConfig")
    private LLMConfig llmConfig;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();


    public DeepSeekProvider() {
        super(null);
    }

    @PostConstruct
    public void init() {
        this.config = llmConfig;
    }

    /**
     * 根据给定的提示文本生成一段内容
     *
     * @param prompt 用于生成内容的提示文本，作为生成内容的依据和指导
     * @return 返回根据提示生成的内容字符串
     */
    @Override
    public String generateText(String prompt) {
        // 1. 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("messages", new Object[]{
                Map.of("role", "user", "content", prompt)
        });
        requestBody.put("temperature", config.getTemperature());
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("stream", false);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        // 2. 发送请求
        String url = config.getBaseUrl() + "/v1/chat/completions";

        // 3. 得到响应
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // 4. 封装结果
        if (response.getStatusCode() == HttpStatus.OK) {
            try {
                JsonNode jsonNode = objectMapper.readTree((response.getBody()));
                String content = jsonNode.path("choices").get(0).path("message").path("content").asText();
                log.info("调用DeepSeek大模型成功: 提示词: {}, 返回结果：{}", prompt, content);
                return content;
            } catch (JsonProcessingException e) {
                log.error("调用DeepSeek大模型失败: {}", prompt);
                throw new RuntimeException(e);
            }
        }
        return "";
    }
}
