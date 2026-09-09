package com.red.common.service;

/**
 * 邮件发送服务的接口
 */
public interface EmailService {
    /**
     * 发送邮件验证码
     * @param to 收件人的邮箱
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String to, String code);

    /**
     * 发送带附件的电子邮件
     * @param email 收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件正文内容
     * @param attachmentName 附件名称
     * @param attachmentType 附件类型
     * @param attachmentBytes 附件内容的字节数组
     * @return 发送成功返回true，失败返回false
     */
    boolean sendEmailWithAttachment(String email, String subject,
                                    String content,
                                    String attachmentName,
                                    String attachmentType,
                                    byte[] attachmentBytes);

}
