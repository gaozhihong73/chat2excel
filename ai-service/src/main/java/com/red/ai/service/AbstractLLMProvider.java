package com.red.ai.service;

import com.red.ai.config.LLMConfig;

/**
 * 大模型提供商的抽象基类
 */
public abstract class AbstractLLMProvider implements LLMProvider {

    protected LLMConfig config;

    public AbstractLLMProvider(LLMConfig config) {
        this.config = config;
    }

    @Override
    public String getProviderName() {
        return config.getProviderName();
    }

    @Override
    public Boolean isAvailable() {
        return config != null;
    }


}
