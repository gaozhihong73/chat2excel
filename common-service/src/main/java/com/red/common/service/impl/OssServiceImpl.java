package com.red.common.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.red.common.config.OssConfig;
import com.red.common.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * OSS服务实现类
 */
@Service
@Slf4j
@ConditionalOnBean(OssConfig.class)
public class OssServiceImpl implements OssService {
    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private OSS ossClient;


    /**
     * 上传文件到指定路径
     *
     * @param file     文件本身
     * @param filePath 文件路径
     * @return
     */
    @Override
    public String uploadFile(MultipartFile file, String filePath) {
        // 1. 获取完整的文件流
        try {
            InputStream inputStream = file.getInputStream();
            String objectKey = createKey(filePath, file.getOriginalFilename());
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    ossConfig.getBucketName(),
                    objectKey,
                    inputStream
            );

            // 2. 调用 oss 的 客户端直接上传代码
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);

            // 3. 返回上传后的 url 地址
            return getUrl(objectKey);
        } catch (IOException e) {
            log.error("上传oss失败{}", file.getOriginalFilename());
            throw new RuntimeException(e);
        }
    }

    /**
     * 从阿里云OSS获取指定对象
     *
     * @param objectKey OSS中对象的键（Key）
     * @return OSSObject 包含对象内容和元数据的对象
     */
    @Override
    public OSSObject getOSSObject(String objectKey) {
        return ossClient.getObject(ossConfig.getBucketName(), objectKey);
    }

    /**
     * 删除阿里云OSS中的指定对象
     *
     * @param ossKey OSS中对象的键（Key）
     */
    @Override
    public void deleteFile(String ossKey) {
        ossClient.deleteObject(ossConfig.getBucketName(), ossKey);
    }


    private String createKey(String filePath, String fileName) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "uploads/" + fileName;
        } else {
            return filePath + fileName;
        }
    }

    private String getUrl(String objectKey) {
        return String.format("https://%s.%s/%s",
                ossConfig.getBucketName(),
                ossConfig.getEndPoint(),
                objectKey
        );
    }
}
