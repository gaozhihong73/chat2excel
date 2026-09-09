package com.red.common.service;

import com.aliyun.oss.model.OSSObject;
import org.springframework.web.multipart.MultipartFile;

public interface OssService {

    /**
     * 上传文件到指定路径
     *
     * @param file     文件本身
     * @param filePath 文件路径
     * @return
     */
    String uploadFile(MultipartFile file, String filePath);


    /**
     * 从阿里云OSS获取指定对象
     *
     * @param objectKey OSS中对象的键（Key）
     * @return OSSObject 包含对象内容和元数据的对象
     */
    OSSObject getOSSObject(String objectKey);

    /**
     * 删除阿里云OSS中的指定对象
     *
     * @param ossKey OSS中对象的键（Key）
     */
    void deleteFile(String ossKey);
}
