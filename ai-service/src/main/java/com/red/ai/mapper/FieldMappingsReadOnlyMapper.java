package com.red.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.red.file.entity.FieldMappingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 只读 FieldMappings 表
 */
@Mapper
public interface FieldMappingsReadOnlyMapper extends BaseMapper<FieldMappingEntity> {
}
