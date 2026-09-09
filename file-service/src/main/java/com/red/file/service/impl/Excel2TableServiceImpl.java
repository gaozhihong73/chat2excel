package com.red.file.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.red.common.service.DistributeLockService;
import com.red.common.util.PinyinUtil;
import com.red.file.service.Excel2TableService;
import com.red.file.service.FieldMappingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

/**
 * Excel 转换 mysql 表 的实现类
 */
@Service
@Slf4j
public class Excel2TableServiceImpl implements Excel2TableService {
    @Autowired
    private DistributeLockService distributeLockService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FieldMappingService fieldMappingService;

    private static final Integer EXCEL_BASE_SIZE = 1000;

    private static final int SQL_BATCH_SIZE = 1000;


    /**
     * 把已有的excel文件转换成mysql表
     *
     * @param file
     * @param fileId
     */
    @Override
    public List<String> convertExcelToTable(MultipartFile file, Long fileId) {
        // 1. 先获取excel的数据
        int sheetCount = getSheetCount(file);
        if (sheetCount == 0) {
            throw new IllegalArgumentException("excel是个空文件");
        }

        // 2. 获取sheet的名称
        List<String> sheetNames = getSheetName(file);


        // 3. 根据文件信息做实际table的转换
        return convertExcelToTable(file, fileId, sheetNames);
    }


    /**
     * 将Excel文件转换为数据库表
     *
     * @param file      上传的Excel文件
     * @param fileId    文件ID
     * @param sheetName Excel中的工作表名称列表
     * @return 生成的表名列表
     */
    private List<String> convertExcelToTable(MultipartFile file, Long fileId, List<String> sheetName) {
        // 1. 生成表名
        String baseTableName = createTableName(); // 创建基础表名
        List<String> tableNames = new ArrayList<>(); // 用于存储生成的表名列表

        // 2. 创建表
        for (int i = 0; i < sheetName.size(); i++) { // 遍历每个工作表
            String tableName = baseTableName + "_sheet" + i; // 为每个工作表生成唯一表名
            createTableFormExcel(tableName, file, fileId, i); // 根据Excel创建表结构

            // 3. 获取file数据
            // 4. 把 file 的数据转换成插入的sql
            // 5. 执行sql
            insertData(tableName, file, i); // 向表中插入Excel数据
            // 记录表名
            tableNames.add(tableName); // 将生成的表名添加到列表中
        }
        return tableNames; // 返回所有生成的表名列表
    }

    /**
     * 插入数据到数据库表
     * 该方法根据文件大小选择不同的处理方式：大文件使用流式处理，小文件一次性读取
     *
     * @param tableName  目标表名
     * @param file       包含要插入数据的Excel文件
     * @param sheetIndex Excel文件中要读取的工作表索引
     */
    public void insertData(String tableName, MultipartFile file, int sheetIndex) {
        // 1. 实现2种读取数据的方式 以文件的大小作为判断的标准
         long fileSize = 5 * 1024 * 1024; // 定义文件大小阈值，5MB

        // Excel 中的列名列表
        List<String> originalHeaders = readHeader(file, sheetIndex);
        // 数据库表的结构
        List<Map<String, Object>> tableStructure = getTableStructure(tableName);
        // 数据库表中除了ID列之外的列名
        List<String> colums = new ArrayList<>();

        // 获取表中每一列列名 除了Id列之外Field -> id
        for (Map<String, Object> column : tableStructure) {
            String columnName = (String) column.get("Field");
            if (!columnName.equals("id")) {
                colums.add(columnName);
            }
        }

        // Excel中的列名 和 数据库中的列名 一一映射
        Map<String, String> headerMapping = new HashMap<>();
        for (int i = 0; i < originalHeaders.size(); i++) {
            String originalHeader = originalHeaders.get(i);
            String safeColum = colums.get(i);
            headerMapping.put(safeColum, originalHeader);
        }

        if (file.getSize() > fileSize) { // 2. 文件大于 5MB 使用流式处理
            // 将headerMapping标记为final以便在匿名类中使用
            final Map<String, String> finalMapping = headerMapping;
            // 将安全列名列表标记为final以便在匿名类中使用
            final List<String> finalSafeColumnNames = colums;
            // 将工作表索引标记为final以便在匿名类中使用
            // 流式获取数据，逐个加入sql语句
            processExcelInBatches(file, sheetIndex, batch -> {
                List<Object[]> batchArgs = new ArrayList<>();
                // 处理每一批次的数据
                for (Map<String, Object> row : batch) {
                    Object[] args = new Object[finalSafeColumnNames.size()];
                    // 为每一列准备参数
                    for (int i = 0; i < finalSafeColumnNames.size(); i++) {
                        // 拿到当前需要的数据库列名 (例如："col_xing_ming")
                        String columnName = finalSafeColumnNames.get(i);
                        String originalHeader = finalMapping.get(columnName);

                        // 根据原始列名从row中获取对应的数据值
                        if (originalHeader != null) {
                            args[i] = row.get(originalHeader);
                        } else {
                            args[i] = null;
                        }
                    }
                    batchArgs.add(args);
                }
                // 执行批量插入操作
                executeBatchInsert(tableName, finalSafeColumnNames, batchArgs);
            });
        } else { // 3. 一次性读取
            // 将文件转换为字节数组
            byte[] fileBytes = getFileBytes(file);
            // 用于存储所有行的参数列表
            List<Object[]> allArgs = new ArrayList<>();
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
                // 获取指定索引的工作表
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                // 获取总行数
                int totalRows = sheet.getLastRowNum();
                // 从第1行开始遍历（跳过表头）
                for (int i = 1; i <= totalRows; i++) {
                    // 获取每一行的数据
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        continue;
                    }
                    // rowData 存储一行数据，键为列名，值为单元格中的值
                    Map<String, Object> rowData = new HashMap<>();
                    for (int j = 0; j < originalHeaders.size(); j++) {
                        Cell cell = row.getCell(j);
                        rowData.put(originalHeaders.get(j), getCellValue(cell));
                    }
                    // 按照字段名的顺序存储值的数组
                    Object[] args = new Object[colums.size()];
                    for (int k = 0; k < colums.size(); k++) {
                        // 数据库中的字段名
                        String safeColumName = colums.get(k);
                        // 通过数据框中的字段名在映射表中获取Excel中的字段名
                        String originalHeader = headerMapping.get(safeColumName);
                        if (originalHeader != null) {
                            args[k] = rowData.get(originalHeader);
                        } else {
                            args[k] = null;
                        }
                    }
                    // 用于存储所有行的参数列表
                    allArgs.add(args);
                }
                // 执行批量插入操作
                executeBatchInsert(tableName, colums, allArgs);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 获取单元格的值并根据单元格类型进行相应处理
     *
     * @param cell Excel单元格对象
     * @return 格式化后的单元格值，以字符串形式返回
     */
    private String getCellValue(Cell cell) {
        // 使用switch表达式根据单元格类型进行不同处理
        return switch (cell.getCellType()) {
            // 处理字符串类型单元格，去除前后空格后返回
            case STRING -> cell.getStringCellValue().trim();
            // 处理数字类型单元格
            case NUMERIC -> {
                // 判断是否为日期格式
                if (DateUtil.isCellDateFormatted(cell)) {
                    // 如果是日期格式，获取日期值并格式化为字符串
                    Date date = cell.getDateCellValue();
                    SimpleDateFormat sdf = new SimpleDateFormat("YY-MM-dd HH:mm:ss");
                    yield sdf.format(date);
                } else {
                    // 如果是普通数字，直接转换为字符串返回
                    double number = cell.getNumericCellValue();
                    yield String.valueOf(number);
                }
            }
            // 处理布尔类型单元格，转换为字符串返回
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            // 处理公式类型单元格，获取公式字符串返回
            case FORMULA -> String.valueOf(cell.getCellFormula());
            // 其他类型单元格返回空字符串
            default -> "";
        };
    }


    /**
     * 执行批量插入操作
     *
     * @param tableName 表名
     * @param columns   列名列表
     * @param args      要插入的数据列表，每个元素是一个Object数组，代表一行数据
     */
    private void executeBatchInsert(String tableName, List<String> columns, List<Object[]> args) {
        // 如果参数列表为空或null，直接返回
        if (args == null || args.isEmpty()) {
            return;
        }

        // 获取列数
        int columnCount = columns.size();

        // 分批处理数据，每批SQL_BATCH_SIZE条记录
        for (int start = 0; start < args.size(); start += SQL_BATCH_SIZE) {
            // 计算当前批次的结束位置
            int end = Math.min(start + SQL_BATCH_SIZE, args.size());

            // 构建SQL语句的INSERT部分
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("insert into `").append(tableName).append("` (");
            // 添加所有列名
            for (String name : columns) {
                sqlBuilder.append("`").append(name).append("`,");
            }
            // 去掉最后一个列名后的逗号
            sqlBuilder.setLength(sqlBuilder.length() - 1);
            sqlBuilder.append(") values ");

            // 参数列表，用于预编译SQL
            List<Object> paramList = new ArrayList<>();

            // 构建SQL语句的VALUES部分
            for (int i = start; i < end; i++) {
                Object[] rowValues = args.get(i);
                sqlBuilder.append("(");
                // 添加每行的参数占位符
                for (int j = 0; j < columnCount; j++) {
                    sqlBuilder.append("?,");
                    paramList.add(rowValues[j]);
                }
                // 去掉每行参数后的逗号
                sqlBuilder.setLength(sqlBuilder.length() - 1);
                sqlBuilder.append("),");
            }

            sqlBuilder.setLength(sqlBuilder.length() - 1); // 去掉最后一个逗号

            jdbcTemplate.update(sqlBuilder.toString(), paramList.toArray());
        }
    }


    /**
     * 分批处理Excel文件
     *
     * @param file           上传的Excel文件
     * @param sheetIndex     要处理的sheet索引
     * @param batchProcessor 批量处理数据的消费者接口
     */
    private void processExcelInBatches(MultipartFile file, int sheetIndex,
                                       Consumer<List<Map<String, Object>>> batchProcessor) {
        // 1. 直接把文件转换成字节数组
        byte[] fileBytes = getFileBytes(file);

        // 2. 使用字节数组输入创建EasyExcel读取器
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes)) {
            EasyExcel.read(byteArrayInputStream, new EasyExcelBatchReadListener(batchProcessor, EXCEL_BASE_SIZE))
                    .sheet(sheetIndex)
                    .headRowNumber(0)
                    .doRead();
        } catch (Exception e) {
            throw new RuntimeException("读取Excel文件时发生错误", e);
        }
    }

    /**
     * EasyExcel批量读取监听器类，继承自AnalysisEventListener，用于处理Excel文件的批量读取
     */
    private static class EasyExcelBatchReadListener extends AnalysisEventListener<Map<Integer, String>> {
        // 批量处理器，用于处理达到批次大小的数据
        private final Consumer<List<Map<String, Object>>> batchProcessor;
        // 批次大小，决定多少条数据组成一批进行处理
        private final int batchSize;
        // 存储Excel表头的列表
        private final List<String> headers = new ArrayList<>();
        // 存储当前批次数据的列表
        private final List<Map<String, Object>> currentBatch = new ArrayList<>();

        /**
         * 构造函数，初始化批量读取监听器
         *
         * @param batchProcessor 批量处理函数，用于处理达到批次大小的数据
         * @param batchSize      每批处理的数据量大小
         */
        private EasyExcelBatchReadListener(Consumer<List<Map<String, Object>>> batchProcessor, int batchSize) {
            this.batchProcessor = batchProcessor;
            this.batchSize = batchSize;
        }

        @Override
        /**
         * 重写父类的invoke方法，用于处理每行数据
         * @param integerStringMap 包含列索引和对应值的Map
         * @param analysisContext 分析上下文对象，提供额外信息
         */
        public void invoke(Map<Integer, String> integerStringMap, AnalysisContext analysisContext) {
            // 如果表头为空，则初始化表头
            if (headers.isEmpty()) {
                // 获取最大列索引
                int maxCol = integerStringMap.keySet().stream().max(Integer::compareTo).orElse(-1);
                // 遍历所有列，将表头数据添加到headers列表中
                for (int i = 0; i <= maxCol; i++) {
                    // 使用getOrDefault方法，如果不存在则使用空字符串作为默认值
                    headers.add(integerStringMap.getOrDefault(i, ""));
                }
                return; // 表头初始化完成后返回
            }
            // 创建一个Map用于存储行数据，键为列名，值为对应的数据
            Map<String, Object> rowData = new HashMap<>();
            // 遍历表头，将每列的数据添加到rowData中
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                String value = integerStringMap.getOrDefault(i, "");
                rowData.put(header, value);
            }
            // 将行数据添加到当前批次
            currentBatch.add(rowData);
            // 如果当前批次达到指定大小，则处理当前批次
            if (currentBatch.size() >= batchSize) {
                // 满了！叫后方的装车工人（数据库插入逻辑）把这整箱搬走
                batchProcessor.accept(currentBatch);
                // 搬走之后，把箱子倒空，准备装下一批！这就是不报 OOM 的根本原因！
                currentBatch.clear();
            }
        }

        @Override
        /**
         * 重写父类的doAfterAllAnalysed方法，在所有数据处理完成后调用
         * @param analysisContext 分析上下文对象，提供额外信息
         */
        public void doAfterAllAnalysed(AnalysisContext analysisContext) {
            // 【修复Bug】：文件全部读取完毕后，如果 currentBatch 里面还有残留的数据（不足 batchSize 的尾数）
            // 必须在这里进行最后一次提交，否则这些数据将会永久丢失！
            if (!currentBatch.isEmpty()) {
                batchProcessor.accept(currentBatch);
                currentBatch.clear(); // 释放内存
            }
        }
    }


    /**
     * 获取表结构
     * 该方法用于查询指定表的表结构信息，返回包含字段名、类型、是否允许NULL、键信息等信息的列表
     *
     * @param tableName 需要查询结构的表名
     * @return 返回一个List集合，其中每个Map代表表中的一个字段，包含字段的各种属性信息
     */
    private List<Map<String, Object>> getTableStructure(String tableName) {
        // 构建SQL查询语句，使用DESCRIBE命令获取表结构
        // 反引号(`)用于确保表名中的特殊字符不会影响SQL语法
        String sql = "describe `" + tableName + "`";
        // 执行SQL查询并将结果转换为List<Map<String, Object>>形式返回
        // 每个Map代表表中的一个字段，键为属性名，值为属性值
        return jdbcTemplate.queryForList(sql);
    }


    /**
     * 创建表
     *
     * @param tableName  表名
     * @param file       文件
     * @param fileId     文件ID
     * @param sheetIndex sheet所在的索引位置
     */
    private void createTableFormExcel(String tableName, MultipartFile file, Long fileId, int sheetIndex) {
        // 1. 读取excel的表头
        List<String> headers = readHeader(file, sheetIndex);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("sheet没有表头");
        }
        // 2. 根据excel的表头生成table字段
        List<String> safeHeaders = new ArrayList<>();
        for (String header : headers) {
            String safeHeader = createColumnName(header);
            safeHeaders.add(safeHeader);
        }

        // 3. 生成sql语句 create table
        StringBuilder sql = new StringBuilder();
        sql.append("create table `").append(tableName).append("` (");
        sql.append(" `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',");
        for (int i = 0; i < safeHeaders.size(); i++) {
            String safeHeader = safeHeaders.get(i);
            String header = headers.get(i);
            sql.append("`").append(safeHeader).append("` TEXT COMMENT '").append(header).append("',");
        }
        sql.setLength(sql.length() - 1);
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Excel导入表'");
        log.info("生成的sql{}", sql);
        // 4. 执行sql语句
        // 创建表
        jdbcTemplate.execute(sql.toString());
        // 保存映射关系到表中
        fieldMappingService.saveMappings(fileId, tableName, headers, safeHeaders);
    }

    /**
     * 创建安全的列名
     * @param headerName
     * @return
     */
    /**
     * 将 Excel 表头名称转换为合法的数据库字段名
     *
     * @param headerName 原始表头（如 "员工姓名", "Age(Year)"）
     * @return 转换后的合法字段名（如 "col_yuangongxingming", "col_Age-Year-"）
     */
    private String createColumnName(String headerName) {
        // 1. 去掉首尾无用的空格
        String cleanName = headerName.trim();

        // 2. 判断是否包含中文字符
        if (!cleanName.matches(".*[\\u4e00-\\u9fa5].*")) {
            // 如果是纯英文/数字：将非字母数字的字符替换为 "-"
            cleanName = cleanName.replaceAll("[^a-zA-Z0-9]", "-");
            // 如果开头不是字母，加上 "col_" 前缀确保合法
            if (!cleanName.matches("[^a-zA-Z].*")) {
                cleanName = "col_" + cleanName;
            }
        } else {
            // 如果包含中文：转换为拼音，并加上 "col_" 前缀
            cleanName = PinyinUtil.chineseToPinYin(cleanName);
            cleanName = "col_" + cleanName;
        }

        // 3. 长度约束：如果超过 64 个字符，强制截断并清理末尾多余下划线
        if (cleanName.length() > 64) {
            cleanName = cleanName.substring(0, 64);
            cleanName = cleanName.replaceAll("_+$", "");
        }

        // 4. 最后的保险：确保最终结果是以字母开头，且只包含字母和数字
        if (!cleanName.matches("^[a-zA-Z][a-zA-Z0-9]*$")) {
            cleanName = "filed_" + cleanName;
        }

        return cleanName.trim();
    }


    /**
     * 读取指定 Excel Sheet 的表头
     * * @param file       上传的文件对象
     *
     * @param sheetIndex Sheet 的索引（从 0 开始）
     * @return 表头列名的列表
     */
    private List<String> readHeader(MultipartFile file, int sheetIndex) {
        // 1. 将文件转为字节数组，方便在内存中反复操作或转为流
        byte[] fileBytes = getFileBytes(file);
        List<String> headers = new ArrayList<>();

        // 使用 try-with-resources 自动关闭字节输入流
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes)) {

            // 2. 定义 EasyExcel 监听器，Map 的 Key 是列索引，Value 是单元格内容
            AnalysisEventListener<Map<Integer, String>> headerListener = new AnalysisEventListener<>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    // 获取当前行的最大列索引
                    int maxCol = data.keySet().stream().max(Integer::compareTo).orElse(-1);
                    // 按照列顺序填充 headers，缺失的列填充空字符串
                    for (int i = 0; i <= maxCol; i++) {
                        headers.add(data.getOrDefault(i, ""));
                    }
                    // 【关键】读取完第一行表头后，抛出异常强制停止解析，避免读取后续海量数据
                    throw new ExcelAnalysisStopException("Header row read completed");
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 解析完成后的回调，此处无需操作
                }
            };

            try {
                // 3. 调用 EasyExcel 执行读取动作
                EasyExcel.read(bais, headerListener)
                        .sheet(sheetIndex)
                        .headRowNumber(0) // 设置为 0 表示不跳过任何行，直接从第一行开始读
                        .doRead();
            } catch (ExcelAnalysisStopException ignore) {
                // 捕获“停止解析”异常，属于正常逻辑，直接忽略即可
            }
        } catch (IOException e) {
            // 捕获 IO 异常并抛出运行时异常
            throw new RuntimeException("文件读取失败", e);
        }

        // 4. 最终校验：如果没读到数据，说明文件或 Sheet 有问题
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("指定 Sheet(索引 " + sheetIndex + ") 没有表头或无法读取表头");
        }

        return headers;
    }


    /**
     * 获取表名
     *
     * @return
     */
    private String createTableName() {
        // 1. 设置一个重试次数
        int maxRetries = 5;
        int retryCount = 1;

        // 2. 设置lockValue
        String lockValue = UUID.randomUUID().toString();
        // 3. 获取了锁之后再去生成表名， 否则就重复
        while (retryCount <= maxRetries) {
            String baseName = "excel_table";
            String tableName = baseName + String.valueOf(System.currentTimeMillis()).substring(8);
            String lockKey = tableName;

            boolean lockAcquired = distributeLockService.tryLock(lockKey, lockValue, 60);
            if (lockAcquired) {
                // 获取分布式锁成功，对表名做个唯一判断
                if (tableExists(tableName)) {
                    distributeLockService.releaseLock(lockKey, lockValue);
                } else {
                    return tableName;
                }
            }
            retryCount++;

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("生成唯一表名失败");
    }

    /**
     * 判断表是否存在
     *
     * @param tableName
     * @return
     */
    private boolean tableExists(String tableName) {
        String sql = "select count(1) from information_schema.tables where table_schema = DATABASE() and table_name = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, tableName) > 0;
    }


    /**
     * 获取 excel 中 sheet 的数量
     *
     * @param file
     * @return
     */
    private int getSheetCount(MultipartFile file) {
        byte[] fileBytes = getFileBytes(file);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes);
        ExcelReader excelReader = EasyExcel.read(byteArrayInputStream).build();
        List<ReadSheet> sheets = excelReader.excelExecutor().sheetList();
        return sheets.size();
    }

    /**
     * 获取 excel 中 sheet 的 名称列表
     *
     * @param file
     * @return
     */
    private List<String> getSheetName(MultipartFile file) {
        byte[] fileBytes = getFileBytes(file);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes);
        ExcelReader excelReader = EasyExcel.read(byteArrayInputStream).build();
        List<ReadSheet> sheets = excelReader.excelExecutor().sheetList();
        ArrayList<String> names = new ArrayList<>();
        for (ReadSheet readSheet : sheets) {
            names.add(readSheet.getSheetName());
        }
        return names;
    }

    private byte[] getFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
