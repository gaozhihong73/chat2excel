package com.red.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.red.ai.entity.AiRequestEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 *  AI 请求记录的Mapper
 */
@Mapper
public interface AiRequestMapper extends BaseMapper<AiRequestEntity> {
}
