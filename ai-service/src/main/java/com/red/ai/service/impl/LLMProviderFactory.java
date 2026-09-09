package com.red.ai.service.impl;

import com.red.ai.service.LLMProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 大模型提供商工厂类
 * 该类用于管理和获取各种大模型提供商实例
 */
@Component  // 将此类标记为Spring组件，以便自动注入和管理
public class LLMProviderFactory {
    // 使用Map存储所有可用的LLM提供商，键为提供商名称，值为提供商实例
    private final Map<String, LLMProvider> providerMap;

    /**
     * 构造函数，通过依赖注入方式初始化所有LLM提供商
     * @param llmProviders 所有实现了LLMProvider接口的bean列表
     */
    public LLMProviderFactory(List<LLMProvider> llmProviders) {
        // 将所有LLM提供商收集到Map中，以提供商名称作为键
        this.providerMap = llmProviders.stream()
                .collect(Collectors.toMap(
                        // 获取每个提供商的名称作为Map的键
                        LLMProvider::getProviderName,
                        // 使用提供商自身作为Map的值
                        Function.identity(),
                        // 当遇到重复键时，保留已存在的值，忽略新的值
                        (existng, replaced) -> existng
                ));
    }


    /**
     * 获取所有可用的LLM提供商列表
     * @return 返回所有状态为可用的LLM提供商列表
     */
    public List<LLMProvider> getAllProviders() {
        // 过滤出状态为可用的提供商，并转换为列表返回
        return providerMap.values().stream()
                .filter(LLMProvider::isAvailable)  // 只保留状态为可用的提供商
                .collect(Collectors.toList());     // 将结果收集为列表
    }


/**
 * 检查指定的LLM提供商是否可用
 * @param providerName 要检查的提供商名称
 * @return 如果提供商存在且可用则返回true，否则返回false
 */
    public boolean isProviderAvailable(String providerName) {
        // 从providerMap中获取指定名称的提供商
        LLMProvider provider = providerMap.get(providerName);
        // 检查提供商是否存在且是否可用
        return provider != null && provider.isAvailable();
    }

    /**
     * 获取指定名称的LLM提供商实例
     * @param providerName 要获取的提供商名称
     * @return 返回指定名称的LLM提供商实例，如果不存在则返回null
     */
    public LLMProvider getProvider(String providerName) {
        // 从providerMap中获取指定名称的提供商
        return providerMap.get(providerName);
    }
}
