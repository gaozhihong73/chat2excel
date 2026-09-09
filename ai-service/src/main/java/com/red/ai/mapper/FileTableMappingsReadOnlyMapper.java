package com.red.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.red.file.entity.FileTableMappingEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileTableMappingsReadOnlyMapper extends BaseMapper<FileTableMappingEntity> {
}
