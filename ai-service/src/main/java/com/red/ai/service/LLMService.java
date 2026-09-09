package com.red.ai.service;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public interface LLMService {

    /**
     * 获取所有可用的大模型列表
     *
     * @return
     */
    List<Map<String, Object>> getProvidersList();

    /**
     * 根据提供商名称切换数据源
     *
     * @param providerName 提供商名称，不能为空
     * @return 返回一个Map<String, String>类型的结果，包含切换后的数据源相关信息
     */
    Map<String, String> switchProvider(@NotBlank(message = "提供商名称不能为空") String providerName);

    /**
     * 获取当前提供者的方法
     * 该方法用于返回当前使用的提供者信息
     *
     * @return 返回一个Map集合，其中键和值都是String类型
     * 键表示提供者的标识，值表示提供者的相关信息
     */
    Map<String, String> getCurrentProvider();

    /**
     * 生成指定提示词的文本内容
     * @param prompt 用户输入的提示词，用于生成文本的依据
     * @return 根据提示词生成的文本内容
     */
    String generateText(String prompt);
}
