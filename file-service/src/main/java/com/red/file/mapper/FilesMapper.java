package com.red.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.red.file.entity.FilesEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件访问数据框的maper
 */
@Mapper
public interface FilesMapper extends BaseMapper<FilesEntity> {

    @Select("select * from files where user_id = #{userId} and id = #{fileId}")
    FilesEntity selectByUserIdAndFileId(@Param("userId") Long userId, @Param("fileId") Long fileId);
}
