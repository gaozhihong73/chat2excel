package com.red.ai.service;

/**
 * 大模型提供商的接口
 */
public interface LLMProvider {
    /**
     * 获取大模型提供商的名称
     * @return 大模型提供商的名称
     */
    String getProviderName();

    /**
     * 检查提供商是否可用
     * @return
     */
    Boolean isAvailable();

    /**
     * 根据给定的提示文本生成一段内容
     *
     * @param prompt 用于生成内容的提示文本，作为生成内容的依据和指导
     * @return 返回根据提示生成的内容字符串
     */
    String generateText(String prompt);
}
