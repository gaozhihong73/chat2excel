package com.red.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志的注解 非侵入式打印日志
 */
@Target(ElementType.METHOD) // 该注解用于指定你的自定义注解可以运用在源代码的哪些位置
@Retention(RetentionPolicy.RUNTIME)  // 该注解用于指定注解的生命周期，即注解在什么时候有效。
public @interface LogOperation {

    /**
     * 操作描述 用于标识业务的含义
     */
    String value() default "";

    /**
     * 是否需要记录请求参数
     */
    boolean logRequest() default true;

    /**
     * 是否需要记录响应参数
     */
    boolean logResponse() default true;

    /**
     * 是否记录接口的调用耗时
     */
    boolean logExecutionTime() default true;
}
