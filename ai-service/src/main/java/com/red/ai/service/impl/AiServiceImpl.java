package com.red.ai.service.impl;

import com.alibaba.excel.util.StringUtils;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red.ai.config.AiRequestStatus;
import com.red.ai.config.ProcessStage;
import com.red.ai.dto.request.AiChatRequest;
import com.red.ai.dto.request.AiRequestHistoryRequest;
import com.red.ai.dto.request.SendEmailRequest;
import com.red.ai.dto.response.*;
import com.red.ai.entity.AiRequestEntity;
import com.red.ai.mapper.AiRequestMapper;
import com.red.ai.service.AiModelService;
import com.red.ai.service.AiService;
import com.red.ai.service.FileMetaDataService;
import com.red.ai.service.SQLGenerationService;
import com.red.common.service.OssService;
import com.red.file.entity.FilesEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiServiceImpl implements AiService {
    @Autowired
    private AiRequestMapper aiRequestMapper;

    @Autowired
    private FileMetaDataService fileMetaDataService;

    @Autowired
    private SQLGenerationService sqlGenerationService;

    @Autowired
    private AiModelService aiModelService;

    @Autowired
    private OssService ossService;

    @Autowired
    @Qualifier("toolCallingChatClient")
    private ChatClient toolCallingChatClient;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROGRESS = "progress";


    /**
     * 统一聊天流式响应方法
     *
     * @param userId        用户ID，用于标识请求的用户
     * @param aiChatRequest AI聊天请求对象，包含聊天内容等相关信息
     * @param sseEmitter    服务器发送事件(SSE)发射器，用于向客户端推送流式数据
     */
    @Override
    public void streamUnifiedChat(Long userId, AiChatRequest aiChatRequest, SseEmitter sseEmitter) {
        // 1. 通知前端，可以开始处理请求了
        AiRequestEntity aiRequestEntity = initStreamRequest(aiChatRequest, userId);
        sendProgressEventWithData(sseEmitter,
                ProcessStage.INIT.getCode(),
                ProcessStage.INIT,
                "开始处理AI请求...",
                null, null, null, null);

        // 2. 校验文件的权限
        List<String> tableNames = validateFileAndSendProgress(aiChatRequest, userId, sseEmitter);

        // 3. 查询表信息（表结构）
        loadTableStructureAndSendProgress(tableNames, sseEmitter);

        // 4. 判断用户输入（查、改、图表）
        TypeResult typeResult = judgeUserInputAndSendProgress(aiChatRequest, sseEmitter);

        // 5. 分叉处理
        AiUnifiedResponse response = null;
        // 5.1 先处理生成图表的逻辑
        if (typeResult.isNeedChart()) {
            response = handleChartFlow(aiChatRequest, aiRequestEntity, sseEmitter);
        } else if (typeResult.isModificationRequest()) {
            // 5.2 执行修改Excel操作
            response = handleModificationChatFlow(aiChatRequest, aiRequestEntity, sseEmitter);
        } else if (!typeResult.continueNext) {
            // 5.3 输入内容与数据处理无关
            response = AiUnifiedResponse.builder()
                    .requestId(aiRequestEntity.getId())
                    .aiResponse("输入内容与数据处理无瓜，请重新输入")
                    .sqlQuery(null)
                    .resultData(Collections.emptyList())
                    .resultCount(0)
                    .status(AiRequestStatus.FAILED.getCode())
                    .needChart(false)
                    .isModificationRequest(false)
                    .modifiedExcelUrl(null)
                    .build();
        } else {
            // 5.4 普通查询
            response = handleQueryChatFlow(aiChatRequest, aiRequestEntity, sseEmitter);
        }

        // 6. 事件处理、汇总
        sendCompleteEvent(sseEmitter, response);
        sseEmitter.complete();
    }

    /**
     * 获取AI请求历史记录
     *
     * @param aiRequestHistoryRequest 包含请求历史查询条件的请求对象
     * @param userId                  用户ID，用于筛选特定用户的请求历史
     * @return 返回一个分页结果，包含AI请求历史记录的响应列表
     */
    @Override
    public IPage<AiRequestHistoryResponse> getRequestsHistory(AiRequestHistoryRequest aiRequestHistoryRequest, Long userId) {
        // 1. 构建查询条件
        // 创建Lambda查询包装器，用于构建数据库查询条件
        LambdaQueryWrapper<AiRequestEntity> queryWrapper = new LambdaQueryWrapper<>();
        // 添加用户ID条件，确保只查询当前用户的历史记录
        queryWrapper.eq(AiRequestEntity::getUserId, userId);
        // 如果请求中包含文件ID，则添加文件ID作为查询条件
        if (aiRequestHistoryRequest.getFileId() != null) {
            queryWrapper.eq(AiRequestEntity::getFileId, aiRequestHistoryRequest.getFileId());
        }
        // 按ID降序排列，确保最新的记录排在前面
        queryWrapper.orderByDesc(AiRequestEntity::getId);


        // 2. 分页查询结果
        // 获取当前页码和每页大小
        long current = aiRequestHistoryRequest.getPageNum();
        long size = aiRequestHistoryRequest.getPageSize();
        // 创建分页对象
        Page<AiRequestEntity> entityPage = new Page<>(current, size);
        // 执行分页查询，获取AI请求实体分页结果
        aiRequestMapper.selectPage(entityPage, queryWrapper);

        // 3. 核心优化：使用 convert 方法进行对象转换
        // convert 方法会自动继承原分页对象的所有元数据（total, size, current, pages 等）
        return entityPage.convert(this::convert);
    }

    /**
     * 发送包含Excel附件的邮件方法
     *
     * @param sendEmailRequest 发送邮件的请求参数对象，包含邮件收件人、主题、内容以及Excel附件等信息
     * @return 发送结果，Boolean类型表示邮件是否发送成功
     */
    @Override
    public Boolean sendEmailWithExcel(SendEmailRequest sendEmailRequest) {
        // 1. 构建邮件内容 （主题 + 附件 + 副标题）
        String[] data = sendEmailRequest.getExcelUrl().split("/");
        String fileName = data[data.length - 1];
        String emailContent = "<html> <body> <h3>您好，这是您请求的Excel文件</h3> <p>文件名：" + fileName + "</p> <p>请查收附件</p> </body> </html>";
        String subject = "修改后的Excel文件" + fileName;

        // 2. 封装发送邮件的工具

        // 3. 封装提示词
        StringBuilder prompt = new StringBuilder();
        prompt.append("请使用sendEmail工具来发送一封邮件\n");
        prompt.append("参数信息如下：\n");
        prompt.append("-email（收件人邮箱）:").append(sendEmailRequest.getEmail()).append("\n");
        prompt.append("-subject（邮件主题）:").append(subject).append("\n");
        prompt.append("-content（邮件正文）:").append(emailContent).append("\n");
        prompt.append("-attachmentUrl（附件URL）:").append(sendEmailRequest.getExcelUrl()).append("\n");
        prompt.append("\n请立即调用sendEmail工具");

        // 4. 调用LLM，触发 tool calling 来发送邮件
        String response = toolCallingChatClient.prompt()
                .user(prompt.toString())
                .call()
                .content();

        // 5. 构造响应
        return response.contains("成功");
    }

    // 实体类转换为历史响应的DTO
    private AiRequestHistoryResponse convert(AiRequestEntity aiRequestEntity) {
        return AiRequestHistoryResponse.builder()
                .id(aiRequestEntity.getId())
                .fileId(aiRequestEntity.getFileId())
                .userInput(aiRequestEntity.getUserInput())
                .aiResponse(aiRequestEntity.getAiResponse())
                .status(aiRequestEntity.getStatus())
                .build();
    }

    /**
     * 处理修改聊天流程的方法
     * 该方法负责处理聊天请求的完整流程，包括发送处理事件、执行修改流程和封装响应
     *
     * @param aiChatRequest   聊天请求对象，包含用户输入的聊天内容
     * @param aiRequestEntity AI请求实体对象，包含请求相关的元数据
     * @param sseEmitter      服务器发送事件(SSE)发射器，用于向客户端推送实时消息
     * @return 返回一个统一的AI响应对象，包含处理结果和相关信息
     */
    private AiUnifiedResponse handleQueryChatFlow(AiChatRequest aiChatRequest, AiRequestEntity aiRequestEntity, SseEmitter sseEmitter) {
        // 1. 发送开始处理事件，通知客户端处理已开始
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.PROCESS_CHAT, null, null, null, null, null);

        // 2. 执行修改流程，处理实际的聊天请求逻辑
        AiChatResponse aiChatResponse = executeQueryChatFlow(aiChatRequest, aiRequestEntity, sseEmitter);

        // 3. 封装响应，将处理结果构造成统一的响应格式返回
        return processChatResponse(aiChatRequest,
                aiRequestEntity,
                new StringBuilder(),
                sseEmitter,
                aiChatResponse,
                aiChatResponse.getResultData(),
                aiChatResponse.getSqlQuery());
    }

    /**
     * 执行普通查询数据流程的方法
     *
     * @param aiChatRequest   包含用户输入和文件ID的请求对象
     * @param aiRequestEntity 请求实体，包含请求ID等信息
     * @param sseEmitter      用于发送服务器发送事件(SSE)的发射器，用于实时向客户端推送处理进度
     * @return AiChatResponse 包含处理结果、SQL语句、修改数据等信息
     * @throws IllegalArgumentException 当无法从用户输入中获取有效表名时抛出
     */
    private AiChatResponse executeQueryChatFlow(AiChatRequest aiChatRequest, AiRequestEntity aiRequestEntity, SseEmitter sseEmitter) {
        // 1. 获取当前关联文件的所有表名
        List<String> tableNames = getTableNamesByFileId(aiChatRequest.getFileId());

        // 2. 根据用户输入，依靠模型判断应该操作哪一张表
        String tableName = determineTargetTable(aiChatRequest.getUserInput(), tableNames, aiChatRequest.getFileId());
        if (tableName == null || StringUtils.isBlank(tableName)) {
            throw new IllegalArgumentException("用户输入无效，无法获取到表名");
        }

        // 3. 结合表结构和用户输入的命令，生成最终的查询SQL
        String sql = generateSql(tableName, aiChatRequest.getUserInput());
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.QUERY_SQL,
                "生成查询sql", sql, null, null, null);

        // 4. 执行SQL，修改Excel文件
        List<Map<String, Object>> result = sqlGenerationService.executeQuery(sql, tableName);
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.EXECUTE_QUERY_SQL,
                "执行查询sql", null, result, result.size(), null);

        // 6. 构建统一格式的响应
        return AiChatResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse("")
                .sqlQuery(sql)
                .resultData(result)
                .resultCount(result.size())
                .status(AiRequestStatus.SUCCESS.getCode())
                .isModificationRequest(true)
                .modifiedExcelUrl(null)
                .build();
    }

    /**
     * 处理修改聊天流程的方法
     * 该方法负责处理聊天请求的完整流程，包括发送处理事件、执行修改流程和封装响应
     *
     * @param aiChatRequest   聊天请求对象，包含用户输入的聊天内容
     * @param aiRequestEntity AI请求实体对象，包含请求相关的元数据
     * @param sseEmitter      服务器发送事件(SSE)发射器，用于向客户端推送实时消息
     * @return 返回一个统一的AI响应对象，包含处理结果和相关信息
     */
    private AiUnifiedResponse handleModificationChatFlow(AiChatRequest aiChatRequest, AiRequestEntity aiRequestEntity, SseEmitter sseEmitter) {
        // 1. 发送开始处理事件，通知客户端处理已开始
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.PROCESS_CHAT, null, null, null, null, null);

        // 2. 执行修改流程，处理实际的聊天请求逻辑
        AiChatResponse aiChatResponse = executeModificationFlow(aiChatRequest, aiRequestEntity, sseEmitter);

        // 3. 封装响应，将处理结果构造成统一的响应格式返回
        return processChatResponse(aiChatRequest,
                aiRequestEntity,
                new StringBuilder(),
                sseEmitter,
                aiChatResponse,
                aiChatResponse.getResultData(),
                aiChatResponse.getSqlQuery());
    }

    /**
     * 处理AI聊天响应的方法
     *
     * @param aiChatRequest   AI聊天请求对象
     * @param aiRequestEntity AI请求实体
     * @param aiResponse      用于构建AI响应的StringBuilder对象
     * @param sseEmitter      SSE发射器，用于发送事件
     * @param aiChatResponse  AI聊天响应对象
     * @param resultData      结果数据列表
     * @param sqlQuery        SQL查询语句
     * @return 返回统一的AI响应对象
     */
    private AiUnifiedResponse processChatResponse(
            AiChatRequest aiChatRequest,        // AI聊天请求对象
            AiRequestEntity aiRequestEntity,    // AI请求实体
            StringBuilder aiResponse,            // AI响应构建器
            SseEmitter sseEmitter,              // SSE事件发射器
            AiChatResponse aiChatResponse,      // AI聊天响应对象
            List<Map<String, Object>> resultData, // 结果数据列表
            String sqlQuery                     // SQL查询语句
    ) {
        // 1. 生成AI响应
        // String aiResponseResult = aiModelService.generateAiResponse(aiChatRequest.getUserInput(), null);
        // String aiResponseResult = "已经成功根据您的要求执行了相关的操作！";

        // aiResponse.append(aiResponseResult);   // 将生成的响应结果追加到响应构建器中
        // 2. 发送AI响应事件，包含进度、阶段、结果数据等信息
        sendProgressEventWithData(
                sseEmitter,                    // SSE发射器
                PROGRESS,                      // 进度事件类型
                ProcessStage.AI_RESPONSE,      // 处理阶段：AI响应
                null,                          // 额外数据
                null,                          // SQL查询语句
                resultData,                    // 结果数据
                resultData.size(),             // 结果数量
                aiResponse.toString()          // 当前AI响应内容
        );

        // 3. 更新AI请求记录状态为成功，并保存响应内容
        aiRequestEntity.setAiResponse(aiChatResponse.toString());  // 设置AI响应内容
        aiRequestEntity.setStatus(AiRequestStatus.SUCCESS.getCode()); // 设置请求状态为成功
        aiRequestMapper.updateById(aiRequestEntity);                // 更新数据库中的请求记录

        // 4. 返回响应对象
        return AiUnifiedResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse(aiResponse.toString())
                .sqlQuery(sqlQuery)
                .resultData(resultData)
                .resultCount(resultData.size())
                .status(AiRequestStatus.SUCCESS.getCode())
                .needChart(false)
                .isModificationRequest(true)
                .modifiedExcelUrl(aiChatResponse.getModifiedExcelUrl())
                .build();
    }


    /**
     * 执行修改数据流程的方法
     * 该方法处理用户对Excel文件的数据修改请求，包括表名确定、SQL生成、执行SQL、数据查询和结果上传等步骤
     *
     * @param aiChatRequest   包含用户输入和文件ID的请求对象
     * @param aiRequestEntity 请求实体，包含请求ID等信息
     * @param sseEmitter      用于发送服务器发送事件(SSE)的发射器，用于实时向客户端推送处理进度
     * @return AiChatResponse 包含处理结果、SQL语句、修改数据等信息
     * @throws IllegalArgumentException 当无法从用户输入中获取有效表名时抛出
     */
    private AiChatResponse executeModificationFlow(AiChatRequest aiChatRequest, AiRequestEntity aiRequestEntity, SseEmitter sseEmitter) {
        // 1. 获取当前关联文件的所有表名
        List<String> tableNames = getTableNamesByFileId(aiChatRequest.getFileId());

        // 2. 根据用户输入，依靠模型判断应该操作哪一张表
        String tableName = determineTargetTable(aiChatRequest.getUserInput(), tableNames, aiChatRequest.getFileId());
        if (tableName == null || StringUtils.isBlank(tableName)) {
            throw new IllegalArgumentException("用户输入无效，无法获取到表名");
        }

        // 3. 结合表结构和用户输入的命令，生成最终的修改SQL
        String sql = generateUpdateSql(tableName, aiChatRequest.getUserInput());
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.UPDATE_SQL,
                "生成修改sql", sql, null, null, null);

        // 4. 执行SQL，修改Excel文件
        int count = sqlGenerationService.executeUpdate(sql);
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.EXECUTE_UPDATE_SQL,
                "执行修改sql", null, null, count, null);

        // 5. 把已经修改后的数据再查一遍，上传到OSS
        List<Map<String, Object>> result = sqlGenerationService.executeQuery("select * from " + tableName, tableName);
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.QUERY_UPDATE_DATA,
                "修改完成，再查数据", null, null, count, null);
        String excelDownLoadUrl = generateModifiedExcel(result, aiChatRequest.getFileId());
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.CREATE_EXCEL,
                "上传修改后的数据到oss", null, result, result.size(), null);

        // 6. 构建统一格式的响应
        return AiChatResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse("")
                .sqlQuery(sql)
                .resultData(result)
                .resultCount(result.size())
                .status(AiRequestStatus.SUCCESS.getCode())
                .isModificationRequest(true)
                .modifiedExcelUrl(excelDownLoadUrl)
                .build();
    }

    /**
     * 生成修改后的Excel文件并上传至OSS服务器
     *
     * @param result  包含要写入Excel的数据列表，每个元素是一个Map，键为列名，值为对应单元格的值
     * @param filedId 原始文件的ID，用于获取原始文件名
     * @return 返回上传至OSS后的文件下载地址
     */
    private String generateModifiedExcel(List<Map<String, Object>> result, Long filedId) {
        // 1. 根据fileId获取原始文件信息
        String fileName = getFileById(filedId).getFileName();

        // 2. 根据原始文件信息，生成修改后的Excel文件
        try {
            // 创建新的工作簿，使用SXSSFWorkbook以支持大数据量
            Workbook workbook = WorkbookFactory.create(true);
            /*
             * 创建名为"修改后的数据"的工作表
             * 设置Excel的样式，包括表头样式和数据单元格样式
             * 处理数据并写入Excel文件，最后调整列宽并输出
             */
            // 创建名为"修改后的数据"的工作表
            Sheet sheet = workbook.createSheet("修改后的数据");
            /*
             * 设置Excel表头的样式
             * 包括字体加粗、字体大小和背景颜色
             */
            // 设置 excel 的样式
            CellStyle cellStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);          // 设置字体加粗
            headerFont.setFontHeightInPoints((short) 12);  // 设置字体大小为12
            cellStyle.setFont(headerFont);      // 应用字体到样式
            cellStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());  // 设置背景颜色为浅绿色
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);  // 设置填充模式为实心

            /*
             * 设置Excel数据单元格的样式
             * 包括设置单元格的边框样式
             */
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderTop(BorderStyle.THIN);     // 设置上边框为细线
            dataStyle.setBorderBottom(BorderStyle.THIN);  // 设置下边框为细线
            dataStyle.setBorderLeft(BorderStyle.THIN);    // 设置左边框为细线
            dataStyle.setBorderRight(BorderStyle.THIN);   // 设置右边框为细线

            /*
             * 获取数据的第一行，用于确定列的顺序
             * 过滤掉"id"列，只保留有效的列
             */
            // 获取第一行
            Map<String, Object> firstRow = result.get(0);
            List<String> columOrder = new ArrayList<>(firstRow.keySet());  // 获取所有列的键
            List<String> validColumns = new ArrayList<>();  // 用于存储有效的列
            for (String column : columOrder) {
                if (!"id".equalsIgnoreCase(column)) {  // 排除"id"列
                    validColumns.add(column);
                }
            }

            /*
             * 写入Excel表头
             * 使用之前定义的表头样式
             */
            Row headerRow = sheet.createRow(0);  // 创建第一行作为表头
            int colIndex = 0;
            for (String column : validColumns) {
                Cell cell = headerRow.createCell(colIndex++);  // 创建单元格并设置列名
                cell.setCellValue(column);  // 设置单元格值为列名
                cell.setCellStyle(cellStyle);  // 应用表头样式
            }

            /*
             * 写入Excel数据行
             * 遍历数据集合，为每一行创建Excel行
             * 使用之前定义的数据单元格样式
             */
            // 写入数据行
            int rowIndex = 1;  // 从第二行开始写入数据
            for (Map<String, Object> row : result) {
                Row excelRow = sheet.createRow(rowIndex++);  // 创建新行
                colIndex = 0;
                for (String column : validColumns) {
                    Cell cell = excelRow.createCell(colIndex++);  // 创建单元格
                    cell.setCellStyle(dataStyle);  // 应用数据单元格样式
                    Object value = row.get(column);  // 获取单元格值
                    if (value != null) {  // 处理非空值
                        if (value instanceof Number) {  // 如果是数字类型
                            cell.setCellValue(((Number) value).doubleValue());  // 设置为double值
                        } else {  // 其他类型
                            cell.setCellValue((String) value);  // 设置为字符串值
                        }
                    }
                }
            }

            /**
             * 自动调整列宽以适应内容
             * 遍历所有列，应用自动调整
             */
            // 自动调整列宽
            for (int i = 0; i < validColumns.size(); i++) {
                sheet.autoSizeColumn(i);  // 自动调整第i列的宽度
            }
            /**
             * 将Excel工作簿写入输出流
             * 最后关闭工作簿
             */
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();  // 创建字节数组输出流
            workbook.write(outputStream);  // 将工作簿写入输出流
            workbook.close();  // 关闭工作簿

            byte[] excelBytes = outputStream.toByteArray();
            MultipartFile multipartFile = new MultipartFile() {
                @Override
                public String getName() {
                    return fileName;
                }

                @Override
                public String getOriginalFilename() {
                    return fileName;
                }

                @Override
                public String getContentType() {
                    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                }

                @Override
                public boolean isEmpty() {
                    return excelBytes.length == 0;
                }

                @Override
                public long getSize() {
                    return excelBytes.length;
                }

                @Override
                public byte[] getBytes() throws IOException {
                    return excelBytes;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(excelBytes);
                }

                @Override
                public void transferTo(File dest) throws IOException, IllegalStateException {
                    throw new UnsupportedEncodingException("当前数据文件格式不支持excel");
                }
            };
            // 3. 把表格传到oss，返回下载地址
            String url = ossService.uploadFile(multipartFile, "modified_excel/");
            log.info("下载文件的地址：{}", url);
            return url;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 生成更新SQL语句的方法
     *
     * @param tableName 需要更新的表名
     * @param userInput 用户输入的条件或更新内容
     * @return 生成的SQL更新语句
     */
    private String generateUpdateSql(String tableName, String userInput) {
        // 1. 先获取表结构信息，包括字段名、数据类型等
        List<Map<String, Object>> tableStructure = sqlGenerationService.getTableStructure(tableName);

        // 2. 集合表结构和用户输入去封装提示词，获取AI响应中的SQL
        String sql = aiModelService.getUpdateSql(userInput, tableName, tableStructure);

        return sql;
    }


    /**
     * 处理图表生成流程的方法
     * 该方法接收聊天请求、请求实体和SSE发射器，执行图表生成的完整流程，并返回统一的AI响应结果
     *
     * @param aiChatRequest   AI聊天请求对象，包含用户输入的对话内容
     * @param aiRequestEntity AI请求实体对象，用于存储请求和响应数据
     * @param sseEmitter      SSE发射器，用于向客户端发送服务器推送事件
     * @return AiUnifiedResponse 统一的AI响应对象，包含生成的图表数据和相关信息
     */
    private AiUnifiedResponse handleChartFlow(AiChatRequest aiChatRequest, AiRequestEntity aiRequestEntity, SseEmitter sseEmitter) {
        // 1. 发送处理对话的事件通知客户端当前处于处理对话阶段
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.PROCESS_CHAT,
                null, null, null, null, null);

        // 2. 生成图标所需要的数据
        AiChartResponse aiChartResponse = generateChart(aiChatRequest, sseEmitter);

        // 3. 发送后续处理的事件
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.CREATE_CHART,
                null, null, null, null, null);

        // 4. 把生成的数据保存到数据库
        aiRequestEntity.setAiResponse(JSON.toJSONString(aiChartResponse));
        aiRequestMapper.updateById(aiRequestEntity);

        // 5. 封装响应数据
        return AiUnifiedResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse(aiChartResponse.getChartDescription())
                .sqlQuery(aiChartResponse.getGeneratedSql())
                .resultData(aiChartResponse.getChartData())
                .resultCount(aiChartResponse.getChartData().size())
                .status(AiRequestStatus.SUCCESS.getCode())
                .needChart(true)
                .chartId(aiChartResponse.getChartId())
                .chartType(aiChartResponse.getChartType())
                .chartData(JSON.toJSONString(aiChartResponse.getChartData()))
                .chartDataList(aiChartResponse.getChartData())
                .xlabel(aiChartResponse.getXlabel())
                .ylabel(aiChartResponse.getYlabel())
                .fileName(aiChartResponse.getFileName())
                .build();
    }

    /**
     * 生成图表响应的方法
     *
     * @param aiChatRequest AI聊天请求，包含用户输入和文件ID等信息
     * @param sseEmitter    服务器发送事件发射器，用于向客户端推送进度信息
     * @return AiChartResponse 包含图表数据的响应对象
     */
    private AiChartResponse generateChart(AiChatRequest aiChatRequest, SseEmitter sseEmitter) {
        // 1. 获取当前文件关联的所有表名
        List<String> tableNames = getTableNamesByFileId(aiChatRequest.getFileId());
        // 2. 需要根据用户输入的内容，结合AI大模型筛选出来要处理的MySQL表
        String tableName = determineTargetTable(aiChatRequest.getUserInput(), tableNames, aiChatRequest.getFileId());

        // 3. 结合表结构与用户输入命令，生成所需要的SQL语句
        String sql = generateSql(tableName, aiChatRequest.getUserInput());

        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.QUERY_SQL,
                "生成查询sql", sql, null, null, null);

        // 4. 执行SQL语句，查询结果
        List<Map<String, Object>> chartData = sqlGenerationService.executeQuery(sql, tableName);
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.EXECUTE_QUERY_SQL,
                "执行sql", sql, chartData, chartData.size(), null);

        // 5. 结构化封装，集合AI去生成二维平面图的X轴和Y轴命名
        ChartLabels chartLabels = generateChartLabelsWithAI(aiChatRequest.getUserInput(), chartData);

        // 6. 判断图像的类型
        String chartType = determineChartType(aiChatRequest.getUserInput());

        // 7. 构建返回给前端的响应
        return AiChartResponse.builder()
                .chartId(String.valueOf(System.currentTimeMillis()))
                .chartType(chartType)
                .generatedSql(sql)
                .chartData(chartData)
                .xlabel(chartLabels.getXlabel())
                .ylabel(chartLabels.getYlabel())
                .fileId(aiChatRequest.getFileId())
                .fileName(getFileById(aiChatRequest.getFileId()).getFileName())
                .build();
    }

    private FilesEntity getFileById(Long fileId) {
        return fileMetaDataService.getFileById(fileId);
    }

    /**
     * 根据用户输入确定图表类型
     *
     * @param userInput 用户输入的字符串
     * @return 返回对应的图表类型字符串，可能是"pie"、"line"或"bar"
     */
    private String determineChartType(String userInput) {
        // 判断用户输入是否包含表示扇形图/饼图的关键词
        if (userInput.contains("扇形") || userInput.contains("饼") || userInput.contains("占比")) {
            return "pie";  // 扇形图
        } else if (userInput.contains("折线") || userInput.contains("line")) {
            return "line";  // 折线图
        }
        return "bar"; // 柱状图
    }

    /**
     * 根据用户提示词和图表数据生成AI图表标签
     *
     * @param prompt    用户输入的提示词
     * @param chartData 图表数据列表，每个元素是一个包含字段名和对应值的Map
     * @return ChartLabels 包含X轴和Y轴标签的对象
     */
    private ChartLabels generateChartLabelsWithAI(String prompt, List<Map<String, Object>> chartData) {
        // 1. 构造AI提示词
        StringBuilder dataSummary = new StringBuilder(); // 用于构建数据摘要的字符串构建器
        dataSummary.append("数据字段："); // 添加数据字段标题
        dataSummary.append(String.join(", ", chartData.get(0).keySet())); // 添加第一个数据项的所有字段名
        dataSummary.append("\n数据行数：").append(chartData.size()); // 添加数据总行数
        // 如果有多行数据，添加数据样本
        if (chartData.size() > 1) {
            dataSummary.append("\n数据样本："); // 添加数据样本标题
            Map<String, Object> sampleRow = chartData.get(1); // 获取第二行数据作为样本
            sampleRow.forEach((key, value) -> // 遍历样本数据的每个字段
                    dataSummary.append(key).append("=").append(value).append(", ") // 添加字段名和值
            );
        }

        // 构建完整的AI提示词
        String result = String.format("" +
                        "你是一个专业的数据分析师，请根据用户需求和数据摘要，生成图表的X轴和Y轴标签。\n" +
                        "用户需求: %s\n" +
                        "数据摘要: %s\n" +
                        "请分析数据特点，生成合适的轴标签:\n" +
                        "1. xlabel应该是分类（如：姓名、成绩、地区）\n" +
                        "2. ylabel应该是数值（如：成绩分数，年龄大小，百分比）\n" +
                        "3. 标签不要太长，不超过10个字符\n" +
                        "示例： \n" +
                        "如果数据是[姓名：张三， 月薪：5000]\n" +
                        "将来的结果: {\"xlabel\":\"姓名\", \"ylabel\":\"月薪(元)\"}\n" +
                        "结果以json的格式返回"
                , prompt, dataSummary
        );

        // 调用AI服务生成响应
        String aiResponse = aiModelService.generateAiResponse(result, chartData);

        // 解析AI响应并返回图表标签
        ChartLabels chartLabels = parseChatLabelsFromAI(aiResponse);
        return chartLabels;
    }

    /**
     * 从AI响应中解析图表标签信息
     *
     * @param aiResponse AI返回的JSON格式字符串
     * @return 解析后的ChartLabels对象，如果解析失败则返回默认标签
     */
    private ChartLabels parseChatLabelsFromAI(String aiResponse) {
        try {
            // 使用ObjectMapper将JSON字符串解析为ChartLabels对象
            return objectMapper.readValue(aiResponse, ChartLabels.class);
        } catch (JsonProcessingException e) {
            // 如果JSON解析失败，返回默认的图表标签（类别和数值）
            return new ChartLabels("类别", "数值");
        }
    }

    /**
     * 根据表名和用户输入生成SQL查询语句
     *
     * @param tableName 表名，用于获取表结构信息
     * @param userInput 用户输入的查询需求
     * @return 生成的SQL查询语句
     */
    private String generateSql(String tableName, String userInput) {
        // 1. 先获取表结构信息，包括字段名、数据类型等
        List<Map<String, Object>> tableStructure = sqlGenerationService.getTableStructure(tableName);

        // 2. 集合表结构和用户输入去封装提示词，获取AI响应中的SQL
        String sql = aiModelService.getSql(userInput, tableName, tableStructure);

        return sql;
    }

    /**
     * 根据用户输入和文件ID确定目标表名
     *
     * @param userInput  用户输入的查询内容
     * @param tableNames 可选的表名列表
     * @param fileId     文件ID
     * @return 确定的目标表名，如果无法确定则返回null
     */
    private String determineTargetTable(String userInput, List<String> tableNames, Long fileId) {
        // 如果只有一个表名，直接返回该表名
        if (tableNames.size() == 1) {
            log.info("excel是单sheet的，只有一张表");
            return tableNames.get(0);
        }

        // 1. 提取关键词
        List<String> keyFields = aiModelService.getFieldsFromUserInput(userInput);

        // 2. 遍历关键词来获取mysql表
        for (String field : keyFields) {
            String tableName = fileMetaDataService.getTableNameByFileIdAndHeader(fileId, field);
            if (tableName != null) {
                return tableName;
            }
        }
        return null;
    }


    /**
     * 根据用户的输入来判断要执行什么样的操作（查、改、图表、拒绝）
     * 该方法用于分析用户输入的内容，判断是否需要生成图表、执行修改操作或拒绝请求，
     * 并将判断结果封装返回
     *
     * @param aiChatRequest AI聊天请求对象，包含用户输入信息
     * @param sseEmitter    服务器发送事件发射器，用于向客户端发送进度信息
     * @return TypeResult 包含判断结果和描述信息的对象
     */
    private TypeResult judgeUserInputAndSendProgress(AiChatRequest aiChatRequest, SseEmitter sseEmitter) {
        String description = "";
        // 1. 判断是否需要生成图表
        boolean needChart = requiresChartGeneration(aiChatRequest.getUserInput());

        if (needChart) {
            description = "需要生成图表";
            TypeResult typeResult = new TypeResult(needChart, false, true, description);

            sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.ANALYZE_INPUT, description,
                    null, null, null, null);
            return typeResult;
        }

        // 2. 判断是否需要执行修改操作
        boolean isModificationRequest = isModificationGeneration(aiChatRequest.getUserInput());
        if (isModificationRequest) {
            description = "需要修改数据";
            TypeResult typeResult = new TypeResult(needChart, isModificationRequest, true, description);
            sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.ANALYZE_INPUT, description,
                    null, null, null, null);
            return typeResult;
        }

        // 3. 判断是否需要拒绝
        boolean isContinue = isContinueGeneration(aiChatRequest.getUserInput());
        if (!isContinue) {
            description = "是否继续";
            TypeResult typeResult = new TypeResult(needChart, isModificationRequest, false, description);
            sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.ANALYZE_INPUT, description,
                    null, null, null, null);
            return typeResult;
        }

        // 4. 普通查询
        description = "普通查询";
        TypeResult typeResult = new TypeResult(needChart, isModificationRequest, true, description);
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.ANALYZE_INPUT, description,
                null, null, null, null);
        return typeResult;
    }

    /**
     * 判断用户输入是否需要继续执行的方法
     * 该方法通过构建提示词并调用AI模型来判断用户输入是否属于需要拒绝执行的情况
     *
     * @param userInput 用户输入的字符串内容
     * @return 如果用户输入需要继续执行则返回true，否则返回false
     */
    private boolean isContinueGeneration(String userInput) {
        // 1. 构建提示词：系统提示词+用户提示词
        // 提示词包含判断标准和用户输入内容
        String prompt = String.format(
                "请判断一下用户输入是否需要拒绝执行，标准如下：\n" +
                        "- 只允许处理跟数据相关的内容：查询数据、修改数据、绘制图表\n" +
                        "- 假如用户的问题是非正向的，直接返回否 \n" +
                        "- 请回答 `是` 或者 `否`即可， 不要去解释， 也不要输出多余的内容\n" +
                        "\n 用户的输入是: %s", userInput
        );
        // 2. 去调用大模型
        String response = aiModelService.generateAiResponse(prompt, null);
        return response.equals("是");
    }

    /**
     * 判断用户输入是否需要执行数据修改操作的辅助方法
     *
     * @param userInput 用户输入的文本内容
     * @return 如果需要执行修改操作返回true，否则返回false
     */
    private boolean isModificationGeneration(String userInput) {
        // 1. 构建提示词：系统提示词+用户提示词
        String prompt = String.format(
                "请判断一下用户输入是否需要执行数据的修改操作，标准如下：\n" +
                        "- 只允许执行修改操作，修改操作包含：加、减、乘、除 \n" +    // 这里只执行update, insert和delete操作，同学课下完成
                        "- 假如用户有新增和删除数据的意图，直接返回否 \n" +
                        "- 相近的词可以替换，例如：改正也可以认为是修改 \n" +
                        "- 请回答 `是` 或者 `否`即可， 不要去解释， 也不要输出多余的内容\n" +
                        "\n 用户的输入是: %s", userInput
        );
        // 2. 去调用大模型
        String response = aiModelService.generateAiResponse(prompt, null);
        return response.equals("是");
    }

    /**
     * 判断用户输入是否需要生成图表
     *
     * @param userInput 用户输入的字符串
     * @return 如果需要生成图表返回true，否则返回false
     */
    private boolean requiresChartGeneration(String userInput) {
        // 1. 构建提示词：系统提示词+用户提示词
        String prompt = String.format(
                "请判断一下用户输入是否需要生成图表，标准如下：\n" +
                        "- 只有当用户明确表达出需要绘制图表的时候，再给他绘制图表 \n" +
                        "- 绘制的图表只能选择三种：柱状图、折线图、扇形图 \n" +
                        "- 相近的词可以替换，例如：扇形图也可以称作饼状图 \n" +
                        "- 请回答 `是` 或者 `否`即可， 不要去解释， 也不要输出多余的内容\n" +
                        "\n 用户的输入是: %s", userInput
        );

        // 2. 调用大模型
        String response = aiModelService.generateAiResponse(prompt, null);
        return response.trim().equals("是");
    }

    /**
     * 加载表结构并发送进度信息
     *
     * @param tableNames 表名列表
     * @param sseEmitter SSE发射器，用于发送服务器发送事件
     */
    private void loadTableStructureAndSendProgress(List<String> tableNames, SseEmitter sseEmitter) {
        // 1. 遍历表名列表，处理每个表的结构信息
        for (String tableName : tableNames) {
            // 获取当前表的结构信息
            List<Map<String, Object>> tableStructure = sqlGenerationService.getTableStructure(tableName);
            // 发送进度事件，包含表名和字段数量信息
            sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.GET_TABLE_STRUCTURE, "当前的表: " + tableName + "共有" + tableStructure.size() + "个字段",
                    null, null, null, null);

        }
    }

    /**
     * 验证文件并发送处理进度
     *
     * @param aiChatRequest AI聊天请求对象，包含文件ID等信息
     * @param userId        用户ID，用于验证文件权限
     * @param sseEmitter    服务器发送事件发射器，用于向客户端推送进度信息
     * @return List<String> 返回表名列表，供后续处理使用
     */
    private List<String> validateFileAndSendProgress(AiChatRequest aiChatRequest, Long userId, SseEmitter sseEmitter) {
        // 1. 先来发送验证文件事件
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.VALIDATE_FILE,
                null, null, null, null, null);

        // 2. 判断文件的权限
        FilesEntity filesEntity = getFileById(userId, aiChatRequest.getFileId());
        if (filesEntity == null) {
            throw new IllegalArgumentException("文件不存在或没有权限");
        }

        // 3. 获取表名列表
        List<String> tableNames = getTableNamesByFileId(aiChatRequest.getFileId());
        String tableNameInfo = tableNames.size() == 1 ? tableNames.get(0) :
                tableNames.size() + "个表" + String.join(", ", tableNames);

        // 4. 发送事件
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.GET_TABLE_NAMES, "表名: " + tableNameInfo,
                null, null, null, null);
        return tableNames;
    }

    /**
     * 根据文件ID获取对应的表名列表
     *
     * @param fileId 文件ID，用于查询关联的表名
     * @return 返回与该文件ID关联的所有表名列表
     */
    private List<String> getTableNamesByFileId(Long fileId) {
        // 调用fileMetaDataService服务方法，根据文件ID获取表名列表
        return fileMetaDataService.getTableNameByFileId(fileId);
    }

    /**
     * 根据用户ID和文件ID获取文件实体对象
     *
     * @param userId 用户ID，用于标识用户
     * @param fileId 文件ID，用于标识文件
     * @return 返回FilesEntity对象，包含文件详细信息
     */
    private FilesEntity getFileById(Long userId, Long fileId) {
        // 调用fileMetaDataService的getFileById方法获取文件元数据信息
        return fileMetaDataService.getFileById(userId, fileId);
    }

    /**
     * 初始化流式请求实体，将聊天请求数据转换为AI请求实体并保存到数据库
     *
     * @param aiChatRequest 聊天请求对象，包含用户输入和文件ID等信息
     * @param userId        用户ID，用于标识请求发起者
     * @return AiRequestEntity 初始化后的AI请求实体对象
     */
    private AiRequestEntity initStreamRequest(AiChatRequest aiChatRequest, Long userId) {
        // 创建新的AI请求实体对象
        AiRequestEntity aiRequestEntity = new AiRequestEntity();
        // 设置用户ID
        aiRequestEntity.setUserId(userId);
        // 设置文件ID，关联相关文件资源
        aiRequestEntity.setFileId(aiChatRequest.getFileId());
        // 设置用户输入内容
        aiRequestEntity.setUserInput(aiChatRequest.getUserInput());
        // 设置请求状态为处理中
        aiRequestEntity.setStatus(AiRequestStatus.PROCESSING.getCode());
        // 初始化AI响应内容为空字符串
        aiRequestEntity.setAiResponse("");
        // 将请求实体插入数据库进行持久化
        aiRequestMapper.insert(aiRequestEntity);
        // 返回初始化完成的请求实体
        return aiRequestEntity;
    }

    /**
     * 发送带有数据的进度事件
     *
     * @param sseEmitter        事件发送器，用于发送Server-Sent Events
     * @param eventType         事件类型，如"progress"、"completed"或"error"
     * @param processStage      处理状态枚举，表示当前的处理阶段
     * @param detail            详细信息，用于描述当前状态或错误信息
     * @param sqlQuery          SQL查询语句，用于展示执行的SQL
     * @param resultPreview     SQL查询结果预览，展示部分查询结果
     * @param resultCount       查询结果总数，表示查询返回的记录数量
     * @param aiResponseContent AI回复内容，展示AI生成的响应
     */
    private void sendProgressEventWithData(
            SseEmitter sseEmitter, // 事件发送器，用于向客户端发送SSE事件
            String eventType, // 事件类型，标识事件的性质
            ProcessStage processStage, // 处理状态枚举，表示当前处理阶段
            String detail, // 数据内容
            String sqlQuery, // SQL查询语句
            List<Map<String, Object>> resultPreview, // SQL查询结果
            Integer resultCount, // 查询结果总数
            String aiResponseContent // AI回复内容
    ) {
        StreamProcessEvent event = StreamProcessEvent.builder()
                .eventType(eventType)
                .stage(processStage.getCode())
                .progress(processStage.getProgress())
                .message(processStage.getDesc())
                .detail(detail)
                .completed("completed".equals(eventType))
                .result(null)
                .error("error".equals(eventType) ? detail : null)
                .sqlQuery(sqlQuery)
                .resultPreview(resultPreview)
                .resultCount(resultCount)
                .aiResponseContent(aiResponseContent)
                .build();
        try {
            sseEmitter.send(SseEmitter.event().name(eventType).data(objectMapper.writeValueAsString(event)));
        } catch (IOException e) {
            log.error("Error sending SSE event: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 发送处理完成事件
     * sseEmitter: 服务器发送事件(SSE)的发送器，用于向客户端推送事件
     * aiUnifiedResponse: 统一的AI响应对象，包含处理结果、数据等信息
     */
    private void sendCompleteEvent(SseEmitter sseEmitter, AiUnifiedResponse aiUnifiedResponse) {
        // 1. 判断当前是查询、生成图表还是修改，并获取结果预览数据
        List<Map<String, Object>> resultPreview = null;
        // 检查是否需要生成图表
        if (aiUnifiedResponse.getNeedChart() != null && aiUnifiedResponse.getNeedChart()) {
            // 如果需要图表，则使用图表数据列表作为预览
            resultPreview = new ArrayList<>(aiUnifiedResponse.getChartDataList());
        } else {
            // 否则，使用结果数据的前5条作为预览（如果结果数据不足5条，则使用全部）
            resultPreview = new ArrayList<>(
                    aiUnifiedResponse.getResultData().subList(0, Math.min(5, aiUnifiedResponse.getResultData().size()))
            );
        }

        // 2. 再去发送完成事件
        StreamProcessEvent event = StreamProcessEvent.builder()
                .eventType("complete")
                .stage(ProcessStage.COMPLETE.getCode())
                .progress(ProcessStage.COMPLETE.getProgress())
                .message("处理完成")
                .detail("处理完成")
                .completed(true)
                .result(aiUnifiedResponse)
                .error(null)
                .sqlQuery(aiUnifiedResponse.getSqlQuery())
                .resultPreview(resultPreview)
                .resultCount(aiUnifiedResponse.getResultCount())
                .aiResponseContent(aiUnifiedResponse.getAiResponse())
                .build();
        try {
            String json = objectMapper.writeValueAsString(event);
            String payload = json == null ? "" : json;
            sseEmitter.send(SseEmitter.event().name("complete").data(payload));
        } catch (Exception e) {
            log.error("发送完成事件失败{},", e.getMessage(), e);
        }
    }


    /**
     * 分析输入阶段的结果对象
     */
    @Data
    @AllArgsConstructor
    private static class TypeResult {
        private final boolean needChart; // 是否需要生成图表
        private final boolean modificationRequest; // 是否需要执行修改操作
        private final boolean continueNext; // 用户命令出圈了，拒绝
        private final String description; // 描述信息
    }
}
