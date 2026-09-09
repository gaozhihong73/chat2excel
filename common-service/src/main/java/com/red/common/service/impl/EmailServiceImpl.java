package com.red.common.service.impl;

import com.red.common.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 发送邮件的实现类
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "spring.mail.username")
public class EmailServiceImpl implements EmailService {

    /**
     * Spring 的邮件发送器
     */
    @Autowired
    private JavaMailSender mailSender;

    /**
     * 发送方邮箱的地址
     */
    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * 构建发送邮件验证码的正文
     *
     * @param code 验证码
     * @return 正文
     */
    private String getContent(String code) {
        return String.format("您好！" +
                        "您的验证码是: %s\n\n" +
                        "验证码的有效期是5分钟，请及时使用",
                code
        );
    }

    @Override
    public boolean sendVerificationCode(String to, String code) {
        // 发送邮件的逻辑
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);

        String subject = "系统验证码";
        String content = getContent(code);

        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);

        log.info("[发送邮件] 验证码 {}  已发送至 {}", code, to);
        return true;
    }

    /**
     * 发送带附件的电子邮件
     *
     * @param email           收件人邮箱地址
     * @param subject         邮件主题
     * @param content         邮件正文内容
     * @param attachmentName  附件名称
     * @param attachmentType  附件类型
     * @param attachmentBytes 附件内容的字节数组
     * @return 发送成功返回true，失败返回false
     */
    @Override
    public boolean sendEmailWithAttachment(String email, String subject, String content, String attachmentName, String attachmentType, byte[] attachmentBytes) {
        // 创建一个MimeMessage对象，用于表示一封邮件
        MimeMessage message = mailSender.createMimeMessage();
        try {
            // 创建MimeMessageHelper对象，支持多部分消息和UTF-8编码
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            // 设置发件人
            helper.setFrom(fromEmail);
            // 设置收件人
            helper.setTo(email);
            // 设置邮件主题
            helper.setSubject(subject);
            // 设置邮件正文内容，支持HTML格式
            helper.setText(content, true);
            // 添加附件，使用字节数组创建资源
            helper.addAttachment(attachmentName, new ByteArrayResource(attachmentBytes), attachmentType);
            // 发送邮件
            mailSender.send(message);
            // 记录邮件发送成功的日志
            log.info("[发送邮件] 邮件 {} 已发送至 {}", subject, email);
            return true;
        } catch (MessagingException e) {
            // 捕获邮件发送过程中的异常，并转换为运行时异常抛出
            throw new RuntimeException(e);
        }
    }
}
