package com.red.ai.service.impl;

import com.red.ai.config.LLMConfig;
import com.red.ai.service.AbstractLLMProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 通义千问系列提供商
 */

@Service
@Slf4j
public class DashScopeProvider extends AbstractLLMProvider {

    @Autowired
    @Qualifier("dashScopeLLMConfig")
    private LLMConfig llmConfig;

    @Autowired
    @Qualifier("defaultChatClient")
    private ChatClient client;


    /**
     * DashScopeProvider类的构造函数
     * 该构造函数继承自父类，并传入null作为参数
     */
    public DashScopeProvider() {
        // 调用父类的构造函数，传入null作为参数
        super(null);
    }

    @PostConstruct
    public void init() {
        this.config = llmConfig;
    }

    /**
     * 重写generateText方法，用于生成文本内容
     *
     * @param prompt 用户输入的提示词
     * @return 模型生成的文本内容
     */
    @Override
    public String generateText(String prompt) {
        // 调用通义千问大模型API，传入用户提示词并获取响应内容
        String response = client.prompt().user(prompt).call().content();
        // 记录调用日志，包含提示词和返回结果，便于后续排查问题
        log.info("调用通义千问大模型，提示词：{}，返回结果：{}", prompt, response);
        // 返回模型生成的文本内容
        return response;
    }
}
