package com.red.ai.service.impl;

import com.red.ai.service.FileMetaDataService;
import com.red.ai.service.LLMService;
import com.red.ai.service.SQLGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 用来生成SQL的服务实现类
 */

@Service
@Slf4j
public class SQLGenerationServiceImpl implements SQLGenerationService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LLMService llmService;

    @Autowired
    private FileMetaDataService fileMetaDataService;

    /**
     * 获取指定表的结构信息
     *
     * @param tableName 需要查询结构的表名
     * @return 返回一个List，其中每个元素是一个Map，包含字段名和对应的属性信息
     * Map的键为String类型，表示字段名
     * Map的值为Object类型，表示字段属性（如类型、长度、是否主键等）
     */
    @Override
    public List<Map<String, Object>> getTableStructure(String tableName) {
        String sql = "DESCRIBE `" + tableName + "`";
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 根据提示信息获取字符串
     *
     * @param prompt 提示信息，用于引导用户输入或获取特定内容的提示
     * @return 返回获取到的字符串内容
     */
    @Override
    public String get(String prompt) {
        return llmService.generateText(prompt);
    }

    /**
     * 执行SQL查询并返回结果集
     *
     * @param sql       要执行的SQL查询语句
     * @param tableName 表名，用于标识查询的来源表
     * @return 包含查询结果的List集合，每个元素是一个Map对象，键为列名，值为对应的数据
     */
    @Override
    public List<Map<String, Object>> executeQuery(String sql, String tableName) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return fileMetaDataService.mapQuery(result, tableName);
    }

    /**
     * 执行SQL更新操作的方法
     *
     * @param sql 要执行的SQL更新语句，如INSERT、UPDATE或DELETE语句
     * @return 受影响的行数，如果发生错误则返回-1
     */
    @Override
    public int executeUpdate(String sql) {
        return jdbcTemplate.update(sql);
    }
}
