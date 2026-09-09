package com.red.ai.function;

import com.red.common.service.EmailService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Function;

/**
 * 邮件发送工具类，让LLM来调用
 */
@Component
@Slf4j
public class EmailSendTool implements Function<EmailSendTool.Request, EmailSendTool.Response> {
    @Autowired
    private EmailService emailService;

    // 声明发送邮件的工具方法
    @Tool
    public Response sendEmail(String email, String subject, String content, String attachmentUrl, String attachmentName) {
        return apply(new Request(email, subject, content, attachmentUrl, attachmentName));
    }

    /**
     * 处理邮件发送请求的方法
     *
     * @param request 包含邮件发送信息的请求对象
     * @return 返回邮件发送结果响应对象
     */
    @Override
    public EmailSendTool.Response apply(EmailSendTool.Request request) {
        // 1. 从URL地址读取字节数组，用于获取附件内容
        byte[] attachmentBytes = null;
        try {
            // 创建URL对象并打开连接
            URL url = new URL(request.attachmentUrl);
            URLConnection connection = url.openConnection();

            // 获取输入流并读取数据到字节数组
            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; // 设置缓冲区大小为4KB
            int bytesRead;
            // 循环读取输入流数据，直到读取完毕
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            attachmentBytes = outputStream.toByteArray(); // 将输出流转换为字节数组
        } catch (Exception e) {
            // 如果读取附件过程中发生异常，返回失败响应
            return new Response(false, "读取附件失败：" + e.getMessage());
        }

        // 2. 调用邮件服务，发送邮件
        // 调用emailService发送带附件的邮件，传入邮件参数和附件内容
        boolean success = emailService.sendEmailWithAttachment(request.getEmail(),
                request.getSubject(),
                request.getContent(),
                request.getAttachmentName(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // 指定附件类型为Excel文件
                attachmentBytes);

        // 根据邮件发送结果返回相应的响应
        if (success) {
            log.info("邮件发送成功，收件人{}", request.getEmail());
            return new Response(true, "邮件发送成功");
        }
        log.info("邮件发送失败，收件人{}", request.getEmail());
        return new Response(false, "邮件发送失败");
    }

    /**
     * 请求类，用于封装邮件发送相关的信息
     * 使用了Lombok的@Data和@AllArgsConstructor注解，自动生成getter、setter、toString等方法以及全参构造方法
     */
    @Data
    @AllArgsConstructor
    public class Request {
        private String email;       // 收件人邮箱地址
        private String subject;    // 邮件主题
        private String content;  // 邮件内容
        private String attachmentUrl;  // 附件地址
        private String attachmentName;  // 附件名称
    }

    @Data
    @AllArgsConstructor
    public class Response {
        private boolean success; // 邮件是否发送成功
        private String message; // 提示文案
    }
}
