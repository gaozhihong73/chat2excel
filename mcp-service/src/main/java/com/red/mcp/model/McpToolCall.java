package com.red.mcp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

// MCP工具调用类
@Data
public class McpToolCall {
    // 工具名称
    @NotBlank(message = "工具名称为必填项")
    private String name;

    // 调用参数
    @NotNull(message = "调用参数为必填项")
    private Map<String, Object> arguments;
}
