package com.red.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Excel 转换 mysql 表 的服务接口
 */
public interface Excel2TableService {

    /**
     * 把已有的excel文件转换成mysql表
     */
    List<String> convertExcelToTable(MultipartFile file, Long fileId);

    /**
     * 向指定表中插入数据 / 一键复原数据
     * @param tableName 目标表的名称
     * @param file 包含数据的Excel文件
     * @param sheetIndex Excel文件中要读取的工作表索引
     */
    void insertData(String tableName, MultipartFile file, int sheetIndex);
}
