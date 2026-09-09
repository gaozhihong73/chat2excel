package com.red.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.red.ai.mapper.FieldMappingsReadOnlyMapper;
import com.red.ai.mapper.FileTableMappingsReadOnlyMapper;
import com.red.ai.mapper.FilesReadOnlyMapper;
import com.red.ai.service.FileMetaDataService;
import com.red.file.entity.FieldMappingEntity;
import com.red.file.entity.FileTableMappingEntity;
import com.red.file.entity.FilesEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI服务下用来读取文件相关数据的实现类
 */
@Service
@Slf4j
public class FileMetaDataServiceImpl implements FileMetaDataService {
    @Autowired
    private FilesReadOnlyMapper filesReadOnlyMapper;

    @Autowired
    private FileTableMappingsReadOnlyMapper fileTableMappingsReadOnlyMapper;

    @Autowired
    private FieldMappingsReadOnlyMapper fieldMappingsReadOnlyMapper;


    /**
     * 根据文件ID获取文件的方法
     */
    @Override
    public FilesEntity getFileById(Long userId, Long fileId) {
        FilesEntity filesEntity = filesReadOnlyMapper.selectById(fileId);
        if (filesEntity != null && filesEntity.getUserId().equals(userId)) {
            return filesEntity;
        }
        return null;
    }

    /**
     * 根据文件ID获取文件的方法
     *
     * @param fileId
     */
    @Override
    public FilesEntity getFileById(Long fileId) {
        return filesReadOnlyMapper.selectById(fileId);
    }

    /**
     * 根据文件ID获取对应的表名列表
     * 该方法通过文件ID查询数据库中关联的表名，并按照工作表索引和ID升序排列后返回。
     * 使用了LambdaQueryWrapper构建查询条件，确保查询结果的有序性。
     *
     * @param fileId 文件ID，用于标识特定的文件
     * @return 返回与该文件ID关联的表名列表，如果没有找到则返回空列表
     */
    @Override
    public List<String> getTableNameByFileId(Long fileId) {
        // 创建LambdaQueryWrapper对象，用于构建数据库查询条件
        LambdaQueryWrapper<FileTableMappingEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 设置查询条件：只选择表名字段
        lambdaQueryWrapper.select(FileTableMappingEntity::getTableName)
                // 设置查询条件：文件ID等于传入的fileId
                .eq(FileTableMappingEntity::getFileId, fileId)
                // 设置排序条件：按工作表索引升序排列
                .orderByAsc(FileTableMappingEntity::getSheetIndex)
                // 设置排序条件：按ID升序排列
                .orderByAsc(FileTableMappingEntity::getId);
        // 执行查询，并将结果转换为Stream流
        // 提取每个实体中的表名
        // 将收集到的表名转换为List并返回
        return fileTableMappingsReadOnlyMapper.selectList(lambdaQueryWrapper).stream()
                .map(FileTableMappingEntity::getTableName)
                .collect(Collectors.toList());
    }

    /**
     * 根据文件ID和表头信息获取对应的表名
     *
     * @param fileId 文件ID，用于标识特定的文件
     * @param field  表头字段名称，用于确定具体的表头信息
     */
    @Override
    public String getTableNameByFileIdAndHeader(Long fileId, String field) {
        // 创建Lambda查询包装器，用于构建查询条件
        LambdaQueryWrapper<FieldMappingEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 设置查询条件：选择表名字段
        lambdaQueryWrapper.select(FieldMappingEntity::getTableName)
                // 设置文件ID等于传入的fileId
                .eq(FieldMappingEntity::getFileId, fileId)
                // 设置原始表头字段等于传入的field
                .eq(FieldMappingEntity::getOriginalHeader, field)
                // 限制查询结果为1条，提高查询效率
                .last("limit 1");
        // 执行查询，获取符合条件的字段映射实体
        FieldMappingEntity fieldMappingEntity = fieldMappingsReadOnlyMapper.selectOne(lambdaQueryWrapper);
        // 如果查询结果为空，则返回null
        if (fieldMappingEntity == null) return null;
        // 返回查询结果中的表名
        return fieldMappingEntity.getTableName();
    }


    /**
     * 将查询结果映射为带有表头的格式化结果
     *
     * @param result    原始查询结果，包含数据库字段名和值的列表
     * @param tableName 数据表名称，用于获取字段映射关系
     * @return 映射后的结果列表，使用原始表头作为键
     */
    @Override
    public List<Map<String, Object>> mapQuery(List<Map<String, Object>> result, String tableName) {
        // 通过表名查询数据库字段与原始表头的映射关系列表
        List<FieldMappingEntity> fieldMappings = listOrderMappings(tableName);
        // 创建一个HashMap用于存储数据库字段名与表头的映射关系
        Map<String, String> map = new HashMap<>();
        // 遍历字段映射列表，构建字段名到表头的映射
        for (FieldMappingEntity fieldMapping : fieldMappings) {
            String dbFieldName = fieldMapping.getDbFieldName();
            String header = fieldMapping.getOriginalHeader();
            // 如果数据库字段名不为空，则将其与表头建立映射关系
            if (StringUtils.hasText(dbFieldName)) {
                // 如果表头为空，则使用数据库字段名作为表头
                String originalHeader = StringUtils.hasText(header) ? header : dbFieldName;
                map.put(dbFieldName, originalHeader);
            }
        }
        // 使用Stream API处理每一行数据，将数据库字段名替换为对应的表头
        return result.stream().map(row -> {
            // 创建一个新的Map用于存储映射后的行数据
            Map<String, Object> mappedRow = new LinkedHashMap<>();
            // 遍历行中的每个字段
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String dbFieldName = entry.getKey();
                Object value = entry.getValue();
                // 获取对应的表头
                String header = map.get(dbFieldName);
                // 如果找到表头，则使用表头作为键；否则使用原始字段名
                if (header != null) {
                    mappedRow.put(header, value);
                } else {
                    mappedRow.put(dbFieldName, value);
                }
            }
            return mappedRow;
        }).collect(Collectors.toList());
    }

    /**
     * 根据表名查询字段映射列表
     *
     * @param tableName 表名
     * @return 返回按照字段顺序和ID升序排列的字段映射实体列表
     */
    private List<FieldMappingEntity> listOrderMappings(String tableName) {
        // 创建Lambda查询包装器
        LambdaQueryWrapper<FieldMappingEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 设置查询条件：表名等于传入的tableName
        // 设置排序规则：先按照字段顺序升序排列，再按照ID升序排列
        lambdaQueryWrapper.eq(FieldMappingEntity::getTableName, tableName)
                .orderByAsc(FieldMappingEntity::getFieldOrder)
                .orderByAsc(FieldMappingEntity::getId);
        // 执行查询并返回结果列表
        return fieldMappingsReadOnlyMapper.selectList(lambdaQueryWrapper);
    }
}
