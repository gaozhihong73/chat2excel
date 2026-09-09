package com.red.ai.config;

import com.red.ai.function.EmailSendTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    /**
     * 创建并配置一个ChatClient Bean
     * 该方法使用Spring Framework的@Bean注解，将返回的ChatClient对象注册为Spring应用上下文中的一个Bean
     *
     * @param chatModel ChatModel实例，用于提供聊天模型的功能支持
     * @return 配置好的ChatClient实例，用于与聊天模型进行交互
     */
    @Bean("defaultChatClient")
    public ChatClient chatClient(ChatModel chatModel) {
        // 使用构建器模式创建ChatClient实例
        // 首先传入chatModel参数，然后调用build()方法完成构建
        return ChatClient.builder(chatModel).build();
    }

    @Bean("toolCallingChatClient")
    public ChatClient toolCallingChatClient(ChatModel chatModel, EmailSendTool emailSendTool) {
        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem("你是一个专业的AI助手，可以使用以下工具:\n" +
                        "1. sendEmail - 发送邮件\n" +
                        "   当用户要求发送邮件时，请使用sendEmail工具"
                )
                .defaultTools(emailSendTool)
                .build();
        return client;
    }
}
