package com.red.file.service;

import java.util.List;
import java.util.Map;

/**
 * 字段映射接口
 */
public interface FieldMappingService {
    /**
     * 保存字段映射关系
     *
     * @param fileId
     * @param tableName
     * @param originalHeader
     * @param dbFieldName
     * @return
     */
    int saveMappings(Long fileId, String tableName, List<String> originalHeader, List<String> dbFieldName);

    /**
     * 获取指定表的字段映射关系
     *
     * @param tableName 表名，用于指定需要获取映射关系的表
     * @return 返回一个Map集合，键为字段名，值为对应的映射关系字符串
     */
    Map<String, String> getMappings(String tableName);

    void deleteByFileId(Long fileId);
}
