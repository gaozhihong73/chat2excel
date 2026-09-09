package com.red.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.red.file.entity.FieldMappingEntity;
import com.red.file.mapper.FieldMappingMapper;
import com.red.file.service.FieldMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FieldMappingServiceImpl implements FieldMappingService {
    @Autowired
    private FieldMappingMapper fieldMappingMapper;

    /**
     * 保存字段映射关系
     *
     * @param fileId
     * @param tableName
     * @param originalHeader
     * @param dbFieldName
     * @return
     */
    @Override
    public int saveMappings(Long fileId, String tableName, List<String> originalHeader, List<String> dbFieldName) {
        List<FieldMappingEntity> mappingEntities = new ArrayList<>();
        for (int i = 0; i < originalHeader.size(); i++) {
            String excelHeader = originalHeader.get(i);
            String dbName = dbFieldName.get(i);

            FieldMappingEntity fieldMappingEntity = new FieldMappingEntity();
            fieldMappingEntity.setFileId(fileId);
            fieldMappingEntity.setTableName(tableName);
            fieldMappingEntity.setDbFieldName(dbName);
            fieldMappingEntity.setOriginalHeader(excelHeader);
            fieldMappingEntity.setFieldOrder(i);
            mappingEntities.add(fieldMappingEntity);
        }
        return fieldMappingMapper.batchInsert(mappingEntities);
    }

    /**
     * 获取指定表的字段映射关系
     * 该方法通过表名查询数据库中的字段映射记录，并将其转换为Map集合返回
     * 使用LinkedHashMap保持字段顺序与数据库中一致
     *
     * @param tableName 表名，用于指定需要获取映射关系的表
     * @return 返回一个Map集合，键为字段名，值为对应的映射关系字符串
     */
    @Override
    public Map<String, String> getMappings(String tableName) {
        // 创建查询条件构造器，用于构建查询条件
        QueryWrapper<FieldMappingEntity> queryWrapper = new QueryWrapper<>();
        // 设置查询条件：表名等于传入的tableName参数
        queryWrapper.eq("table_name", tableName);
        // 执行查询，获取表中所有字段映射记录
        List<FieldMappingEntity> fieldMappingEntities = fieldMappingMapper.selectList(queryWrapper);
        // 创建LinkedHashMap用于存储字段映射关系，保持插入顺序
        Map<String, String> map = new LinkedHashMap<>();
        // 遍历查询结果，将每个字段映射记录存入Map中
        // 键为数据库字段名(dbFieldName)，值为原始表头(originalHeader)
        for (FieldMappingEntity fieldMappingEntity : fieldMappingEntities) {
            map.put(fieldMappingEntity.getDbFieldName(), fieldMappingEntity.getOriginalHeader());
        }
        // 返回包含字段映射关系的Map集合
        return map;
    }

    @Override
    public void deleteByFileId(Long fileId) {
        LambdaQueryWrapper<FieldMappingEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FieldMappingEntity::getFileId, fileId);
        fieldMappingMapper.delete(queryWrapper);
    }
}
