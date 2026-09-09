package com.red.ai.service;

import com.red.file.entity.FilesEntity;

import java.util.List;
import java.util.Map;

/**
 * AI服务下用来读取文件相关数据的接口
 */
public interface FileMetaDataService {
    /**
     * 根据文件ID和用户ID获取文件的方法
     */
    FilesEntity getFileById(Long userId, Long fileId);

    /**
     * 根据文件ID获取文件的方法
     */
    FilesEntity getFileById(Long fileId);

    /**
     * 根据文件ID获取对应的表名列表
     *
     * @param fileId 文件ID，用于标识特定的文件
     * @return 返回与该文件ID关联的表名列表，如果没有找到则返回空列表
     */
    List<String> getTableNameByFileId(Long fileId);

    /**
     * 根据文件ID和表头信息获取对应的表名
     *
     * @param fileId 文件ID，用于标识特定的文件
     * @param field 表头字段名称，用于确定具体的表头信息
     */
    String getTableNameByFileIdAndHeader(Long fileId, String field);

/**
     * 根据表名查询数据并返回结果集
     *
     * @param result 初始查询结果集，包含需要查询的数据
     * @param tableName 要查询的表名
     * @return 返回一个List，其中每个元素都是一个Map，Map的键为String类型，值为Object类型
     *         包含查询到的数据
     */
    List<Map<String, Object>> mapQuery(List<Map<String, Object>> result, String tableName);
}
