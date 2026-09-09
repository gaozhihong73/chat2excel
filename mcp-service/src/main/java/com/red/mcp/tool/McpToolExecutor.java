package com.red.mcp.tool;

import com.red.mcp.model.McpToolResult;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * MCP工具的执行接口
 */
public interface McpToolExecutor {
    // 获取工具名称
    String getToolName();

    // 获取工具描述
    String getToolDesc();

    // 获取工具的参数定义
    Map<String, Object> getInputSchema();

/**
 * 执行Mcp工具的方法
 * @param arguments 调用参数，为必填项，不能为null
 * @return 返回执行结果
 */
    McpToolResult execute(Map<String, Object> arguments);
}
