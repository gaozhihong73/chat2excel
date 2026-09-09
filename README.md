# Chat2Excel - 智能表格助手

> 用自然语言对话操作 Excel —— 上传 Excel 文件，通过 AI 对话完成数据查询、修改与图表生成。

Chat2Excel 是一个基于 **Spring Cloud 微服务架构** 的后端项目。用户上传 Excel 文件后，系统将其转换为 MySQL 数据表；用户通过自然语言提问，由大语言模型（LLM）自动生成并执行 SQL，实现数据查询、数据修改（同步回写生成新 Excel）、图表生成等操作，全过程通过 **SSE 流式推送** 实时展示处理阶段与进度。

## 核心功能

- **Excel 文件管理**：上传（阿里云 OSS 存储）、预览、下载、删除、一键复原
- **Excel → MySQL 表转换**：自动解析 Excel 结构，生成数据表并记录字段映射关系
- **AI 对话处理**（SSE 流式响应，含处理阶段与进度百分比）：
  - **数据查询**：自然语言 → 生成 SQL → 执行 → AI 总结回答
  - **数据修改**：生成 UPDATE SQL → 执行 → 回查结果 → 重新生成 Excel 文件
  - **图表生成**：返回结构化图表数据（图表类型、坐标轴、标题等），前端可直接渲染
  - **自由闲聊**：非数据类问题走普通对话分支
- **多模型支持与热切换**：内置 DashScope（通义千问 qwen-plus）与 DeepSeek（deepseek-chat）两个 Provider，运行时可动态切换
- **邮件发送**：基于 Spring AI Function Calling（`@Tool`），LLM 可自动触发发送带 Excel 附件的邮件
- **用户认证**：邮箱验证码登录、JWT 鉴权、网关统一认证过滤
- **AI 请求历史**：对话请求落库，支持分页查询
- **MCP 服务**：提供 Model Context Protocol 工具接口（AI 对话、文件列表、用户认证）

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言 / 运行时 | Java 21 |
| 基础框架 | Spring Boot 3.2.0、Spring Cloud 2023.0.0、Spring Cloud Alibaba 2022.0.0.0 |
| AI 框架 | Spring AI Alibaba 1.0.0.2（DashScope）、DeepSeek API |
| 注册 / 配置中心 | Nacos |
| 网关 | Spring Cloud Gateway |
| 数据库 | MySQL 8、MyBatis-Plus 3.5.7 |
| 缓存 | Redis（Lettuce） |
| 对象存储 | 阿里云 OSS |
| Excel 处理 | Apache POI 5.2.4、EasyExcel 3.3.4 |
| 认证 | JWT（JJWT 0.11.5） |
| 邮件 | Spring Mail（SMTP） |
| 其他 | Spring Retry、Fastjson2、Pinyin4j、OpenFeign |

## 系统架构

```
                        ┌─────────────────┐
        前端请求  ────▶  │ gateway-service │  JWT 认证 / CORS / 路由转发
                        └────────┬────────┘
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
      ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
      │ user-service │   │ file-service │   │  ai-service  │
      │ 登录/验证码   │   │ Excel/OSS    │   │ LLM/SQL/图表 │
      └──────────────┘   └──────────────┘   └──────────────┘
              └──────────────────┼──────────────────┘
                                 ▼
              MySQL / Redis / Nacos / 阿里云 OSS / LLM API
```

### 模块说明

| 模块 | 端口 | 职责 |
|---|---|---|
| `gateway-service` | 8080 | 统一入口，JWT 认证全局过滤器、CORS、路由转发（`/api/v1/**`，StripPrefix=2） |
| `user-service` | 9001 | 邮箱验证码、登录认证、登出、修改密码、用户信息 |
| `ai-service` | 9002 | AI 核心：LLM 对话、SSE 流式响应、SQL 生成与执行、图表生成、邮件发送、Provider 切换 |
| `file-service` | 9003 | Excel 上传（OSS）、Excel→MySQL 表转换、预览、下载、一键复原、删除 |
| `mcp-service` | 9004 | MCP 服务（stdio + HTTP），暴露 AiChatTool / FileListTool / UserAuthTool |
| `common-service` | - | 公共模块：统一返回结果、JWT 工具、Redis/OSS/邮件封装、分布式锁、全局异常处理、AOP 日志 |

## 快速开始

### 环境要求

- JDK 21
- Maven 3.8+
- MySQL 8.x
- Redis
- Nacos 2.x
- 阿里云 OSS Bucket
- DashScope / DeepSeek API Key

### 1. 准备中间件

启动 MySQL、Redis、Nacos，并创建数据库：

```sql
CREATE DATABASE bite_excel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

主要数据表：`users`、`files`、`file_table_mappings`、`field_mappings`、`ai_requests`（每个上传的 Excel 还会动态生成对应的数据表）。

### 2. 修改配置

各模块的配置托管在 Nacos 配置中心，本地 `bootstrap.yml` 中需配置：

- Nacos 地址、命名空间、账号密码
- MySQL 数据源、Redis 连接
- `spring.ai`：DashScope / DeepSeek 的 API Key 与模型参数
- `aliyun.oss`：endpoint、AccessKey、Bucket 名称
- `spring.mail`：SMTP 邮箱与授权码
- `security.gateway.token`：服务间内部调用令牌

> 建议通过环境变量或 Nacos 配置中心注入敏感信息，不要将真实密钥提交到代码仓库。

### 3. 编译与启动

```bash
# 编译整个项目
mvn clean install -DskipTests

# 按顺序启动各服务（IDE 中运行各模块的 Application 主类，或命令行启动 jar）
java -jar gateway-service/target/gateway-service.jar
java -jar user-service/target/user-service.jar
java -jar file-service/target/file-service.jar
java -jar ai-service/target/ai-service.jar
java -jar mcp-service/target/mcp-service.jar
```

## API 概览

所有接口经网关访问，统一前缀 `/api/v1`：

### 用户模块 `/api/v1/users`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/verification-code` | 发送邮箱验证码 |
| POST | `/auth` | 登录认证（返回 JWT） |
| POST | `/logout` | 登出 |
| POST | `/change-password` | 修改密码 |
| GET | `/info` | 获取当前用户信息 |

### 文件模块 `/api/v1/files`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/upload/single` | 上传单个 Excel 文件 |
| GET | `/list` | 文件列表 |
| GET | `/download` | 下载文件 |
| GET | `/excel/preview/{fileId}` | 预览 Excel 数据 |
| GET | `/excel/info/{fileId}` | 获取 Excel 文件信息 |
| POST | `/restore/{fileId}` | 一键复原文件数据 |
| DELETE | `/delete` | 删除文件 |

### AI 模块 `/api/v1/ai`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/chat/stream` | AI 对话（SSE 流式响应） |
| GET | `/requests` | AI 请求历史（分页） |
| POST | `/send-email` | 发送带 Excel 附件的邮件 |

### 模型管理 `/api/v1/llm`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/providers/list` | 获取可用模型提供商列表 |
| POST | `/providers/switch` | 切换模型提供商 |
| GET | `/providers/current` | 获取当前使用的提供商 |

### MCP 服务（直连 9004）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/tools` | 获取 MCP 工具列表 |
| POST | `/tools/call` | 调用 MCP 工具 |

## 处理流程示例

以「把销售额大于 1000 的行标记为重要」为例：

1. 前端通过 SSE 发起对话请求
2. ai-service 根据文件表结构（`field_mappings`）构造 Prompt，调用 LLM 生成 UPDATE SQL
3. 执行 SQL 修改数据表，回查修改后的数据
4. 基于修改后的数据重新生成 Excel 并上传 OSS
5. 全过程通过 SSE 推送阶段事件（`StreamProcessEvent`），包含处理阶段与进度百分比

## 说明

- 本仓库仅包含后端服务，前端项目不在本仓库中。
- 服务间内部调用通过网关令牌（`security.gateway.token`）校验，各业务服务只信任经网关转发的请求。

## License

本项目仅供学习交流使用。
