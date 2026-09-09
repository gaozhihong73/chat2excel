package com.red.ai.service.impl;

import com.red.ai.service.LLMProvider;
import com.red.ai.service.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LLMServiceImpl implements LLMService {
    @Autowired
    private LLMProviderFactory providerFactory;

    @Value("${spring.ai.default-provider}")
    private String currentProvider;


    /**
     * 获取所有可用的大模型列表
     * 该方法遍历所有提供者(LLMProvider)，并收集它们的名称和可用状态
     *
     * @return 返回一个包含所有提供者信息的列表，每个提供者信息是一个Map，
     * 包含"name"(提供者名称)和"available"(是否可用)两个键值对
     */
    @Override
    public List<Map<String, Object>> getProvidersList() {
        // 创建一个用于存储所有提供者信息的列表
        List<Map<String, Object>> providersList = new ArrayList<>();

        // 遍历所有提供者
        for (LLMProvider provider : providerFactory.getAllProviders()) {
            // 为每个提供者创建一个Map对象
            Map<String, Object> map = new HashMap<>();
            // 将提供者名称添加到Map中
            map.put("name", provider.getProviderName());
            // 将提供者是否可用的状态添加到Map中
            map.put("available", provider.isAvailable());
            // 将当前提供者的信息添加到结果列表中
            providersList.add(map);
        }
        // 返回包含所有提供者信息的列表
        return providersList;
    }

    /**
     * 根据提供商名称切换数据源
     *
     * @param providerName 提供商名称，不能为空
     * @return 返回一个Map<String, String>类型的结果，包含切换后的数据源相关信息
     */
    @Override
    public Map<String, String> switchProvider(String providerName) {
        // 1. providerName校验
        if (!providerFactory.isProviderAvailable(providerName)) {
            throw new IllegalArgumentException("指定的提供商不可用" + providerName);
        }

        // 2. 切换当前的模型提供商
        this.currentProvider = providerName;
        log.info("切换大模型提供商为: {}", providerName);
        HashMap<String, String> map = new HashMap<>();
        map.put("providerName", providerName);
        return map;
    }

    /**
     * 获取当前提供者的方法
     * 该方法用于返回当前使用的提供者信息
     *
     * @return 返回一个Map集合，其中键和值都是String类型
     * 键表示提供者的标识，值表示提供者的相关信息
     */
    @Override
    public Map<String, String> getCurrentProvider() {
        HashMap<String, String> map = new HashMap<>();
        map.put("urrentProvider", this.currentProvider);
        return map;
    }

    /**
     * 生成指定提示词的文本内容
     *
     * @param prompt 用户输入的提示词，用于生成文本的依据
     * @return 根据提示词生成的文本内容
     */
    @Override
    public String generateText(String prompt) {
        LLMProvider provider = providerFactory.getProvider(currentProvider);
        return provider.generateText(prompt);
    }
}
