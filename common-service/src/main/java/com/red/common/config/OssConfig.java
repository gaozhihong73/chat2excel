package com.red.common.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云配置
 */
@Data
@Configuration
/*
这个注解的作用是将配置文件（如 application.yml 或 application.properties）中以 aliyun.oss 开头的属性，自动映射到当前 Java Bean 的字段上。
工作原理：Spring 会根据字段名自动匹配。例如，它会将 aliyun.oss.end-point 的值赋给 endPoint 字段。
优势：相比于使用多个 @Value 注解，它支持松散绑定（例如横杠转驼峰）、JSR-303 数据校验，且结构更清晰。
 */
@ConfigurationProperties(prefix = "aliyun.oss")
/*
理解：按需加载（开关控制）
这是一个“条件注解”。它告诉 Spring：只有当指定的配置项存在时，才加载这个配置类。
判定逻辑：Spring 会检查配置文件中是否存在 aliyun.oss.access-key-id 这个属性。
存在：Spring 会创建 OssConfig 实例并将其中的 ossClient 放入 IOC 容器。
不存在：Spring 会直接忽略这个类，好像它不存在一样。
用途：防止在没有配置密钥的情况下启动 OSS 客户端导致报错，或者实现功能的“插件化”开关。
 */
@ConditionalOnProperty(prefix = "aliyun.oss", name = "access-key-id")

public class OssConfig {
    /**
     * 访问端点
     */
    private String endPoint;

    /**
     * 秘钥ID
     */
    private String accessKeyId;

    /**
     * 秘钥
     */
    private String accessKeySecret;

    /**
     * 存储空间
     */
    private String bucketName;

    /**
     * 最大文件限制（字节）
     */
    private Long maxFileSize;

    @Bean
    public OSS ossClient() {
        return new OSSClientBuilder().build(
                this.endPoint,
                this.accessKeyId,
                this.accessKeySecret
        );
    }
}
