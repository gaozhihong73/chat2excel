package com.red.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.red.file.entity.FileTableMappingEntity;
import com.red.file.mapper.FileTableMappingMapper;
import com.red.file.service.FileTableMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件映射表的服务实现类
 */
@Service
public class FileTableMappingServiceImpl implements FileTableMappingService {

    @Autowired
    private FileTableMappingMapper fileTableMappingMapper;


    /**
     * 保存文件与表格的映射关系
     *
     * @param fileId     文件ID
     * @param tableNames 表格名称列表
     * @param sheetNames 工作表名称列表
     */
    @Override
    public void saveMappings(Long fileId, List<String> tableNames, List<String> sheetNames) {
        // 遍历表格名称列表，为每个表格创建映射关系
        for (int i = 0; i < tableNames.size(); i++) {
            // 创建文件表格映射实体对象
            FileTableMappingEntity fileTableMappingEntity = new FileTableMappingEntity();
            // 设置文件ID
            fileTableMappingEntity.setFileId(fileId);
            // 设置表格名称
            fileTableMappingEntity.setTableName(tableNames.get(i));
            // 检查工作表名称列表是否有效，并设置工作表名称
            if (sheetNames != null && sheetNames.get(i) != null) {
                fileTableMappingEntity.setSheetName(sheetNames.get(i));
            }
            // 设置工作表索引
            fileTableMappingEntity.setSheetIndex(i);
            // 将映射关系保存到数据库
            fileTableMappingMapper.insert(fileTableMappingEntity);
        }
    }

    /**
     * 根据文件ID获取对应的表名列表
     *
     * @param fileId 文件ID
     * @return 表名列表，按照sheet_index升序排列
     */
    @Override
    public List<String> getTableNamesByFileId(Long fileId) {
        // 创建查询条件构造器
        QueryWrapper<FileTableMappingEntity> queryWrapper = new QueryWrapper<>();
        // 设置查询条件：file_id等于传入的fileId，并按照sheet_index升序排列
        queryWrapper.eq("file_id", fileId).orderByAsc("sheet_index");
        return fileTableMappingMapper.selectList(queryWrapper).stream().map(FileTableMappingEntity::getTableName).collect(Collectors.toList());
    }

    /**
     * 根据文件ID删除文件与表的映射关系
     *
     * @param fileId 文件ID，用于标识需要删除的映射记录
     */
    @Override
    public void deleteByFileId(Long fileId) {
        // 创建Lambda查询构造器，用于构建删除条件
        LambdaQueryWrapper<FileTableMappingEntity> queryWrapper = new LambdaQueryWrapper<>();
        // 设置查询条件：文件ID等于传入的fileId
        queryWrapper.eq(FileTableMappingEntity::getFileId, fileId);
        // 执行删除操作，删除满足条件的映射记录
        fileTableMappingMapper.delete(queryWrapper);
    }
}
