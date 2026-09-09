package com.red.file.service;

import java.util.List;

/**
 * 文件映射表的服务类
 */
public interface FileTableMappingService {


    /**
     * 保存文件ID、表名和工作表名称之间的映射关系
     *
     * @param fileId     文件的唯一标识符
     * @param tableNames 数据库表名的列表
     * @param sheetNames Excel工作表名称的列表
     */
    void saveMappings(Long fileId, List<String> tableNames, List<String> sheetNames);

    List<String> getTableNamesByFileId(Long fileId);

    void deleteByFileId(Long fileId);
}
