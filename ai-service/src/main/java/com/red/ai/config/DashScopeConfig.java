package com.red.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通义千问配置信息
 * 该类用于配置和初始化通义千问大语言模型的各项参数
 */
@Configuration
public class DashScopeConfig {

    // 从配置文件中注入API密钥
    @Value("${spring.ai.alibaba.dashscope.api-key}")
    private String apiKey;

    // 从配置文件中注入模型名称
    @Value("${spring.ai.alibaba.dashscope.chat.options.model}")
    private String model;

    // 从配置文件中注入温度参数，控制生成文本的随机性
    @Value("${spring.ai.alibaba.dashscope.chat.options.temperature}")
    private Double temperature;

    // 从配置文件中注入最大令牌数，限制生成文本的最大长度
    @Value("${spring.ai.alibaba.dashscope.chat.options.max-tokens}")
    private Integer maxTokens;

    // 从配置文件中注入连接超时时间（毫秒）
    @Value("${spring.ai.alibaba.dashscope.http.connect-timeout}")
    private Integer connectTimeout;

    // 从配置文件中注入读取超时时间（毫秒）
    @Value("${spring.ai.alibaba.dashscope.http.read-timeout}")
    private Integer readTimeout;

    // 从配置文件中注入写入超时时间（毫秒）
    @Value("${spring.ai.alibaba.dashscope.http.write-timeout}")
    private Integer writeTimeout;

    /**
     * 创建并配置DashScope的LLM配置Bean
     * @return 返回一个配置好的LLMConfig实例
     */
    @Bean("dashScopeLLMConfig")
    public LLMConfig dashScopeLLMConfig() {
        // 创建LLMConfig实例
        LLMConfig config = new LLMConfig();
        // 设置提供商名称为dashscope
        config.setProviderName("dashscope");
        // 设置模型名称
        config.setModel(model);
        // 设置API密钥
        config.setApiKey(apiKey);
        // 设置API基础URL
        config.setBaseUrl("https://dashscope.aliyuncs.com");
        // 设置温度参数
        config.setTemperature(temperature);
        // 设置最大令牌数
        config.setMaxTokens(maxTokens);
        // 设置连接超时时间
        config.setConnectTimeout(connectTimeout);
        // 设置读取超时时间
        config.setReadTimeout(readTimeout);
        // 设置写入超时时间
        config.setWriteTimeout(writeTimeout);
        // 返回配置完成的LLMConfig实例
        return config;
    }
}
