package com.red.mcp.service.impl;

import com.red.mcp.model.McpToolCall;
import com.red.mcp.model.McpToolResult;
import com.red.mcp.service.McpServerService;
import com.red.mcp.tool.McpTool;
import com.red.mcp.tool.McpToolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class McpServerServiceImpl implements McpServerService {
    @Autowired
    private List<McpToolExecutor> toolExecutors;

    /**
     * 获取工具列表的方法
     * 该方法遍历所有工具执行器，构建McpTool对象列表
     *
     * @return 返回一个包含McpTool对象的列表，该列表包含了所有可用的工具信息
     */
    @Override
    public List<McpTool> listTools() {
        // 1. 构建工具的基本信息
        // 使用Stream API处理工具执行器集合
        return toolExecutors.stream()
                .map(executor -> {
                    // 创建McpTool构建器，设置基本属性
                    McpTool.McpToolBuilder builder = McpTool.builder()
                            .name(executor.getToolName())    // 设置工具名称
                            .description(executor.getToolDesc())  // 设置工具描述
                            .inputSchema(executor.getInputSchema());  // 设置输入模式

                    // 获取输入模式
                    Map<String, Object> schema = executor.getInputSchema();
                    if (schema != null) {
                        // 提取属性的定义
                        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

                        // 提取必填字段列表，如果不存在则使用空列表
                        List<String> required = (List<String>) schema.getOrDefault("required", Collections.emptyList());

                        // 每个属性转换
                        List<McpTool.McpParameter> parameters = properties.entrySet().stream()
                                .map(entry -> {
                                    // 3. 将参数的每个值转换为McpParameter
                                    Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                                    return McpTool.McpParameter.builder()
                                            .name(entry.getKey())
                                            .type((String) prop.get("type"))
                                            .description((String) prop.get("description"))
                                            .required(required.contains(entry.getKey()))
                                            .build();
                                })
                                .collect(Collectors.toList());
                        builder.prameters(parameters);
                    }
                    return builder.build();
                    // 4. 统一返回List<McpTool>
                }).collect(Collectors.toList());
    }

    /**
     * 调用指定工具并返回结果
     *
     * @param mcpToolCall 工具调用对象，包含调用工具所需的所有参数和配置信息
     * @return 返回工具调用的结果，结果类型为McpToolResult，包含工具执行后的数据和状态信息
     */
    @Override
    public McpToolResult callTool(McpToolCall mcpToolCall) {
        // 1. 获取工具
        McpToolExecutor executor = toolExecutors.stream()
                .filter(e -> e.getToolName().equals(mcpToolCall.getName()))
                .findFirst()
                .orElse(null);

      if (executor == null) {
          return McpToolResult.builder()
                  .toolName(mcpToolCall.getName())
                  .success(false)
                  .error("工具" + mcpToolCall.getName() + "还未开发， 调用失败。")
                  .build();
      }

     // 2. 考虑工具调用
     return executor.execute(mcpToolCall.getArguments());
    }
}
