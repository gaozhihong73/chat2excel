package com.red.mcp.service;

import com.red.mcp.model.McpToolCall;
import com.red.mcp.model.McpToolResult;
import com.red.mcp.tool.McpTool;

import java.util.List;

/**
 * MCP服务层接口
 */
public interface McpServerService {
    /**
     * 获取工具列表的方法
     *
     * @return 返回一个包含McpTool对象的列表，该列表包含了所有可用的工具信息
     */
    public List<McpTool> listTools();

    /**
     * 调用指定工具并返回结果
     *
     * @param mcpToolCall 工具调用对象，包含调用工具所需的所有参数和配置信息
     * @return 返回工具调用的结果，结果类型为McpToolResult，包含工具执行后的数据和状态信息
     */
    McpToolResult callTool(McpToolCall mcpToolCall);
}
