package com.red.ai.service;

import java.util.List;
import java.util.Map;

/**
 * AI大模型服务接口
 */
public interface AiModelService {
    /**
     * 生成AI响应的方法
     *
     * @param prompt     用户输入的提示文本，用于引导AI生成响应
     * @param resultData 包含相关数据的列表，每个元素是一个Map，存储键值对形式的数据
     * @return 返回AI生成的响应字符串，响应内容基于prompt和resultData生成
     */
    String generateAiResponse(String prompt, List<Map<String, Object>> resultData);


    /**
     * 从用户输入中提取字段列表
     * 该方法接收一个用户输入的字符串，解析并提取其中的字段信息
     *
     * @param userInput 用户输入的字符串，可能包含多个字段信息
     * @return 返回一个包含所有提取出的字段的字符串列表
     * 如果输入为空或无法提取到有效字段，可能返回空列表
     */
    List<String> getFieldsFromUserInput(String userInput);

    /**
     * 根据用户输入、表名和表结构生成SQL查询语句
     *
     * @param userInput      用户输入的查询条件
     * @param tableName      要查询的表名
     * @param tableStructure 表的结构信息，包含字段名、数据类型等
     */
    String getSql(String userInput, String tableName, List<Map<String, Object>> tableStructure);

    /**
     * 根据用户输入、表名和表结构生成SQL修改语句
     *
     * @param userInput      用户输入的查询条件
     * @param tableName      要查询的表名
     * @param tableStructure 表的结构信息，包含字段名、数据类型等
     */
    String getUpdateSql(String userInput, String tableName, List<Map<String, Object>> tableStructure);
}
