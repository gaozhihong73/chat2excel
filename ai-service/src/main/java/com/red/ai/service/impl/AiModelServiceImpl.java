package com.red.ai.service.impl;

import com.red.ai.service.AiModelService;
import com.red.ai.service.SQLGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ai大模型实现类
 */
@Service
@Slf4j
public class AiModelServiceImpl implements AiModelService {

    @Autowired
    private SQLGenerationService sqlGenerationService;

    /**
     * 生成AI响应的方法
     *
     * @param prompt     用户输入的提示文本，用于引导AI生成响应
     * @param resultData 包含相关数据的列表，每个元素是一个Map，存储键值对形式的数据
     * @return 返回AI生成的响应字符串，响应内容基于prompt和resultData生成
     */
    @Override
    public String generateAiResponse(String prompt, List<Map<String, Object>> resultData) {
        // 1. 构建一下响应上下文
        StringBuilder context = new StringBuilder();
        context.append("用户请求: ").append(prompt).append("\n");
        if (resultData != null) {
            context.append("查询结果数量: ").append(resultData.size()).append("\n");
            context.append("查询结果示例：").append(resultData.get(0)).append("\n");
        }
//        context.append("请注意，若是修改类的请求（例如加减乘除），这里给到的数据是已经成功执行相应修改操作后的数据，原数据为当前数据反向操作用户给出的值" +
//                "比如用户要求加100，现在看到的是200，那么这200就已经是加过100的结果了，原数据为200-100=100，请在生成响应的时候考虑到这一点。\n");

        // 2. 使用AI生成响应
        String result = "基于以下信息，生成友好的AI响应" + context;

        return sqlGenerationService.get(result);
    }

    /**
     * 从用户输入中提取字段列表
     * 该方法接收一个用户输入的字符串，解析并提取其中的字段信息
     *
     * @param userInput 用户输入的字符串，可能包含多个字段信息
     * @return 返回一个包含所有提取出的字段的字符串列表
     * 如果输入为空或无法提取到有效字段，可能返回空列表
     */
    @Override
    public List<String> getFieldsFromUserInput(String userInput) {
        // 1. 构建提示词
        // 使用String.format方法构建一个包含用户输入的提示词，用于指导AI如何提取字段
        String prompt = String.format("请从用户的问题中提取出来关键的字段（列名）\n"
                        + "要求：\n"
                        + "  1.只提取用户问题中的内容，不要延伸\n"
                        + "  2.假如提取到多个关键字段，请用逗号分隔开来\n"
                        + "  3.取不到关键字段，直接返回个无\n"
                        + "  4.不要添加任何解释\n"
                        + "示例：\n"
                        + "用户问题：请问苹果手机的价格是多少？\n"
                        + "提取结果：价格,苹果手机\n"
                        + "实际用户输入的问题：%s\n"
                , userInput);

        // 2. 调用大模型获取AI响应
        // 使用sqlGenerationService的get方法发送提示词并获取AI的响应结果
        String response = sqlGenerationService.get(prompt);
        // 3. 处理AI响应
        // 将AI返回的响应按逗号和换行符分割成多个部分
        String[] parts = response.split(",.\n");
        // 4. 创建结果列表
        // 创建一个ArrayList来存储处理后的字段
        List<String> results = new ArrayList<>();
        // 5. 遍历分割后的结果
        // 将分割后的每个部分去除前后空格，并添加到结果列表中
        for (String part : parts) {
            results.add(part.trim());
        }
        // 6. 返回结果
        // 返回包含所有提取字段的列表
        return results;
    }

    /**
     * 根据用户输入、表名和表结构生成SQL查询语句
     *
     * @param userInput      用户输入的查询条件
     * @param tableName      要查询的表名
     * @param tableStructure 表的结构信息，包含字段名、数据类型等
     */
    @Override
    public String getSql(String userInput, String tableName, List<Map<String, Object>> tableStructure) {
        // 1. 提取表字段名
        List<String> headers = tableStructure.stream().map(row -> (String) row.get("Field"))
                .filter(field -> field != null && !field.isEmpty()).toList();

        // 2. 构建提示词
        String prompt = String.format("" +
                        "你是一个SQL专家。能够根据用户的需求生成mysql的查询语句， 特别注意需要使用模糊查询。\n" +
                        "表名：%s \n" +
                        "表结构：%s \n" +
                        "用户需求：%s \n" +
                        "要求：\n" +
                        " 1. 无论用户输入什么内容，你都需要生成SQL查询语句\n" +
                        " 2. 忽略掉绘制等词汇的影响，只提取数据查询需求，并生成SQL\n" +
                        " 3. 生成的SQL必须是可以执行的，不能有语法错误\n" +
                        " 4. 只生成可以执行的SQL语句，不要携带任何额外的内容\n"
                , tableName, headers, userInput
        );

        // 3. 调用大模型获取AI响应
        String response = sqlGenerationService.get(prompt);
        log.info("生成的SQL语句为: {}", response);
        return response.trim();
    }

    /**
     * 根据用户输入、表名和表结构生成SQL修改语句
     *
     * @param userInput      用户输入的查询条件
     * @param tableName      要查询的表名
     * @param tableStructure 表的结构信息，包含字段名、数据类型等
     */
    @Override
    public String getUpdateSql(String userInput, String tableName, List<Map<String, Object>> tableStructure) {
        // 1.提取表字段名
        List<String> headers = tableStructure.stream()
                .map(row -> (String) row.get("Field"))
                .filter(field -> field != null && !field.isEmpty())
                .toList();
        // 2.构建提示词
        String prompt = String.format("" +
                        "你是一个SQL专家。能够根据用户的需求生成mysql的修改语句。\n" +
                        "表名：%s \n"+
                        "表结构：%s \n"+
                        "用户需求：%s \n"+
                        "要求：\n" +
                        " 1. 无论用户输入什么内容，你都需要生成SQL修改语句\n"+
                        " 2. 忽略掉绘制等词汇的影响，只提取数据修改需求，并生成SQL\n"+
                        " 3. 生成的SQL必须是可以执行的，不能有语法错误\n"+
                        " 4. 只生成可以执行的sql语句，不要携带任何额外的内容\n"
                , tableName, headers, userInput
        );
        String response = sqlGenerationService.get(prompt);
        log.info("最终生成的SQL:{}", response.trim());
        return response.trim();
    }
}
