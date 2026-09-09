package com.red.ai.config;

/**
 * AI请求状态枚举
 * 定义了AI请求可能的各种状态及其对应的代码和描述
 */
public enum AiRequestStatus {

    // 处理中状态，代码为0
    PROCESSING(0, "处理中"),
    // 成功状态，代码为1
    SUCCESS(1, "成功"),
    // 失败状态，代码为2
    FAILED(2, "失败"),
    // 部分成功状态，代码为3
    PARTIAL_SUCCESS(3, "部分成功");

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    ;


    // 状态代码，使用Integer类型
    private final Integer code;

    // 状态描述信息
    private final String description;


    /**
     * 枚举构造函数
     *
     * @param code        状态代码
     * @param description 状态描述
     */
    AiRequestStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
}
