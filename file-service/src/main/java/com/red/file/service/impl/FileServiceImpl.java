package com.red.file.service.impl;

import com.aliyun.oss.model.OSSObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.red.common.service.OssService;
import com.red.common.util.FileValidationUtil;
import com.red.file.dto.request.ExcelPreviewRequest;
import com.red.file.dto.request.FileDeleteRequest;
import com.red.file.dto.request.FileListRequest;
import com.red.file.dto.request.FileUploadRequest;
import com.red.file.dto.response.ExcelPreviewResponse;
import com.red.file.dto.response.FileInfoResponse;
import com.red.file.dto.response.FileUploadResponse;
import com.red.file.entity.FilesEntity;
import com.red.file.mapper.FilesMapper;
import com.red.file.service.Excel2TableService;
import com.red.file.service.FieldMappingService;
import com.red.file.service.FileService;
import com.red.file.service.FileTableMappingService;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Autowired
    private OssService ossService;

    @Autowired
    private Excel2TableService excel2TableService;

    @Autowired
    private FilesMapper filesMapper;

    @Autowired
    private FileTableMappingService fileTableMappingService;

    @Autowired
    private FieldMappingService fieldMappingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    /**
     * 文件上传
     *
     * @param file
     * @param request
     * @param userId
     * @return
     */
    @Override
    public FileUploadResponse upload(MultipartFile file, FileUploadRequest request, Long userId) {
        // 1. 校验文件（文件类型、文件大小）
        if (!FileValidationUtil.vavalidateFileFormat(file)) {
            throw new IllegalArgumentException("只能处理excel文件");
        }
        if (!FileValidationUtil.ifOutOfLarge(file)) {
            throw new IllegalArgumentException("文件大小不能超过50MB");
        }

        // 2. 上传文件到阿里云OSS
        String fileUrl = ossService.uploadFile(file, request.getCategory());

        // 3. 创建文件记录
        FilesEntity filesEntity = FilesEntity.builder()
                .userId(userId)
                .fileName(file.getOriginalFilename())
                .filePath(request.getCategory() == null ? "upload" : request.getCategory())
                .ossKey(getOssKey(fileUrl))
                .fileSize(file.getSize())
                .build();
        filesMapper.insert(filesEntity);

        // 4. 把 excel 转化为 mysql 的表
        List<String> tableNames = excel2TableService.convertExcelToTable(file, filesEntity.getId());
        fileTableMappingService.saveMappings(filesEntity.getId(), tableNames, null);

        // 5. 构建响应
        return FileUploadResponse.builder()
                .fileId(filesEntity.getId())
                .fileName(filesEntity.getFileName())
                .fileSize(filesEntity.getFileSize())
                .fileUrl(fileUrl)
                .ossKey(filesEntity.getOssKey())
                .uploadStatus(1)
                .build();
    }

    /**
     * 分页查询文件列表信息
     *
     * @param request 文件列表请求参数，包含查询条件等信息
     * @return IPage<FileInfoResponse> 返回分页后的文件信息响应结果
     */
    @Override
    public IPage<FileInfoResponse> list(FileListRequest request) {
        // 1. 构建查询对象
        // 创建QueryWrapper对象，用于构建数据库查询条件
        QueryWrapper<FilesEntity> queryWrapper = new QueryWrapper<>();

        // 2. 在查询对象中间去构建请求参数
        // 设置用户ID的查询条件，精确匹配
        queryWrapper.eq("user_id", request.getUserId());
        // 如果文件名不为空，则添加文件名的模糊查询条件
        if (StringUtils.isNotBlank(request.getFileName())) {
            queryWrapper.like("file_name", request.getFileName());
        }
        // 如果上传状态不为空，则添加上传状态的精确查询条件
        if (request.getUploadStatus() != null) {
            queryWrapper.eq("upload_status", request.getUploadStatus());
        }
        // 按ID降序排序
        queryWrapper.orderByDesc("id");

        // 3. 查询出结构，创建分页对象
        // 获取当前页码，如果为空则默认为1
        long current = request.getPageNum() != null ? request.getPageNum() : 1;
        // 获取每页大小，如果为空则默认为10
        long size = request.getPageSize() != null ? request.getPageSize() : 10;
        // 创建分页对象
        Page<FilesEntity> page = new Page<>(current, size);
        // 执行分页查询，获取实体分页结果
        IPage<FilesEntity> entityIPage = filesMapper.selectPage(page, queryWrapper);

        // 4. 构建响应
        // 将实体列表转换为响应列表
        List<FileInfoResponse> responseList = entityIPage.getRecords().stream().map(this::convert).collect(Collectors.toList());
        // 创建响应分页对象
        Page<FileInfoResponse> responsePage = new Page<>(current, size);
        // 设置响应记录
        responsePage.setRecords(responseList);
        // 设置总记录数
        responsePage.setTotal(entityIPage.getTotal());
        // 设置总页数
        responsePage.setPages(entityIPage.getPages());
        // 返回响应分页结果
        return responsePage;
    }

    /**
     * 下载文件的方法
     *
     * @param fileId   文件ID，用于标识需要下载的文件
     * @param response HTTP响应对象，用于将文件内容返回给客户端
     * @param userId   用户ID，用于验证用户是否有权限下载该文件
     */
    @Override
    public void downloadFile(Long fileId, HttpServletResponse response, Long userId) {
        // 1. 查询文件信息
        FilesEntity filesEntity = filesMapper.selectById(fileId);
        if (fileId == null) {  // 检查文件ID是否为空
            log.error("文件不存在{}", fileId);  // 记录错误日志
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);  // 设置HTTP状态码为404
            try {
                response.getWriter().write("{\"error\" : \" 文件不存在\"}");  // 返回错误信息
            } catch (IOException e) {
                throw new RuntimeException(e);  // 抛出运行时异常
            }
            return;  // 提前结束方法
        }

        // 2. 校验用户是否有权限下载该文件
        if (!filesEntity.getUserId().equals(userId)) {  // 检查用户ID是否匹配
            log.error("用户没有权限下载该文件{}", fileId);  // 记录错误日志
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);  // 设置HTTP状态码为403
            try {
                response.getWriter().write("{\"error\" : \"用户没有权限下载该文件\"}");  // 返回错误信息
            } catch (IOException e) {
                throw new RuntimeException(e);  // 抛出运行时异常
            }
        }

        // 3. 从oss上去下载文件
        response.reset();  // 清空response缓冲区
        response.setContentType(FileValidationUtil.getContentType(filesEntity.getFileName()));  // 设置响应内容类型
        response.setCharacterEncoding("UTF-8");  // 设置字符编码为UTF-8
        OSSObject ossObject = ossService.getOSSObject(filesEntity.getOssKey());  // 从OSS获取文件对象
        // 设置文件大小
        long contentLength = ossObject.getObjectMetadata().getContentLength();  // 获取文件大小
        response.setContentLengthLong(contentLength);  // 设置响应内容长度
        response.setBufferSize(65536);  // 设置缓冲区大小为64KB

        try {
            InputStream inputStream = ossObject.getObjectContent();  // 获取输入流
            ServletOutputStream outputStream = response.getOutputStream();  // 获取输出流
            byte[] buffer = new byte[65536];  // 创建64KB的缓冲区
            int byteRead = 0;  // 读取的字节数
            long totalBytesRead = 0;  // 总读取字节数
            // 循环读取文件内容并写入输出流
            while ((byteRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, byteRead);  // 写入数据
                totalBytesRead += byteRead;  // 更新总读取字节数

                // 每读取1MB数据就刷新一次输出流
                if (totalBytesRead % (1024 * 1024) == 0) {
                    outputStream.flush();
                }
            }
            outputStream.flush();  // 刷新输出流
        } catch (IOException e) {
            throw new RuntimeException(e);  // 抛出运行时异常
        }
    }

    /**
     * 预览Excel文件的方法
     *
     * @param fileId  文件ID，用于标识需要预览的Excel文件
     * @param userId  用户ID，用于验证用户是否有权限预览该文件
     * @param request Excel预览请求参数，包含分页信息等
     * @return ExcelPreviewResponse 预览Excel文件的响应对象
     **/
    @Override
    public ExcelPreviewResponse previewExcel(Long fileId, Long userId, ExcelPreviewRequest request) {
        // 1. 校验文件权限
        // 根据用户ID和文件ID查询文件实体
        FilesEntity filesEntity = filesMapper.selectByUserIdAndFileId(userId, fileId);
        // 如果文件不存在或用户无权限，抛出异常
        if (filesEntity == null) {
            throw new IllegalArgumentException("文件不存在或者用户无权限");
        }

        // 2.根据fileId获取所有的表
        // 获取与文件关联的所有表名列表
        List<String> tableNames = fileTableMappingService.getTableNamesByFileId(fileId);
        // 获取当前需要预览的表名（根据请求中的sheet索引）
        String currentTableName = tableNames.get(request.getSheetIndex());
        // 获取当前表的总记录数
        Long totalRecords = getTotalRecords(currentTableName);

        // 3.从表中获取数据，然后构建响应
        // 构建Excel预览响应对象，包含Excel信息、工作表信息、当前工作表索引、列标题、数据行和分页信息
        return ExcelPreviewResponse.builder()
                .excelInfo(getExcelInfo(fileId))                    // 获取Excel基本信息
                .sheets(tableNames.size() > 1 ? buildSheetInfoList(tableNames) : null)  // 构建工作表信息列表（如果有多张工作表）
                .currentSheetIndex(request.getSheetIndex())          // 设置当前工作表索引
                .headers(getColumnHeaders(currentTableName))         // 获取当前工作表的列标题
                .dataRows(getPageData(currentTableName, request.getPage(), request.getPageSize()))  // 获取分页数据
                .paginationInfo(getPaginationInfo(request.getPage(), request.getPageSize(), totalRecords))  // 获取分页信息
                .build();
    }

    /**
     * 获取分页信息的方法
     *
     * @param page         当前页码
     * @param pageSize     每页记录数
     * @param totalRecords 总记录数
     * @return 返回包含分页信息的PaginationInfo对象
     */
    private ExcelPreviewResponse.PaginationInfo getPaginationInfo(Integer page, Integer pageSize, Long totalRecords) {
        // 计算总页数，使用Math.ceil向上取整
        long totalPage = (long) Math.ceil((double) totalRecords / pageSize);
        // 使用构建器模式创建并返回PaginationInfo对象
        return ExcelPreviewResponse.PaginationInfo.builder()
                .currentPage(page)              // 设置当前页码
                .pageSize(pageSize)            // 设置每页记录数
                .totalPages(totalPage)         // 设置总页数
                .totalRecords(totalRecords)    // 设置总记录数
                .hasNext(page < totalPage)     // 判断是否有下一页
                .hasPrevious(page > 1)        // 判断是否有上一页
                .build();                      // 构建并返回PaginationInfo对象
    }

    /**
     * 根据表名、页码和每页大小获取分页数据
     *
     * @param tableName 表名
     * @param page      当前页码，从1开始
     * @param pageSize  每页记录数
     * @return 包含查询结果的列表，每个元素是一个Map，表示一行数据，不包含id字段
     */
    private List<Map<String, Object>> getPageData(String tableName, Integer page, int pageSize) {
        // 计算偏移量，(页码-1)*每页大小
        int offset = (page - 1) * pageSize;
        // 构建SQL查询语句，使用LIMIT和OFFSET实现分页
        String sql = "SELECT * FROM " + tableName + " ORDER BY id  LIMIT ? OFFSET ?";
        // 执行查询，获取指定页的数据
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, pageSize, offset);
        // 使用Stream处理查询结果，移除每行数据中的id字段
        return rows.stream().map(row -> {
            Map<String, Object> newRow = new HashMap<>(row);
            // 移除id字段
            newRow.remove("id");
            return newRow;
        }).collect(Collectors.toList());
    }

    /**
     * 根据表名获取列头信息
     *
     * @param tableName 表名
     * @return 返回Excel预览响应的列头信息列表
     */
    private List<ExcelPreviewResponse.ColumnHeader> getColumnHeaders(String tableName) {
        // 获取字段映射关系
        Map<String, String> mappings = fieldMappingService.getMappings(tableName);
        // 创建列头信息列表
        List<ExcelPreviewResponse.ColumnHeader> columnHeaders = new ArrayList<>();
        // 遍历字段映射关系，构建列头信息
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            columnHeaders.add(ExcelPreviewResponse.ColumnHeader.builder()
                    .dbFieldName(entry.getKey())  // 设置数据库字段名
                    .originalHeader(entry.getValue())  // 设置原始列头
                    .build());
        }
        return columnHeaders;
    }


    /**
     * 构建Excel工作表信息列表
     *
     * @param tableNames 表名列表
     * @return 包含每个工作表信息的列表
     */
    private List<ExcelPreviewResponse.SheetInfo> buildSheetInfoList(List<String> tableNames) {
        // 创建工作表信息列表
        List<ExcelPreviewResponse.SheetInfo> sheetInfoList = new ArrayList<>();
        // 初始化计数器
        int i = 0;
        // 遍历表名列表
        for (String tableName : tableNames) {
            // 构建工作表信息对象并添加到列表中
            sheetInfoList.add(ExcelPreviewResponse.SheetInfo.builder()
                    .sheetName(tableName)  // 设置工作表名称为表名
                    .totalRows(getTotalRecords(tableName))  // 获取总行数
                    .totalColumns(getTotalColumns(tableName))  // 获取总列数
                    .sheetName("sheet_" + i++)  // 设置工作表名称为sheet_x格式
                    .build());
        }
        // 返回构建完成的工作表信息列表
        return sheetInfoList;
    }

    /**
     * 根据文件ID获取Excel文件信息的方法
     *
     * @param fileId 文件ID
     * @return ExcelPreviewResponse.ExcelInfo 包含Excel文件信息的对象
     */
    public ExcelPreviewResponse.ExcelInfo getExcelInfo(Long fileId) {
        // 根据文件ID查询文件实体信息
        FilesEntity filesEntity = filesMapper.selectById(fileId);
        // 获取与该文件关联的所有表名列表
        List<String> tableNames = fileTableMappingService.getTableNamesByFileId(fileId);
        // 获取第一个表名（假设Excel内容存储在第一个表中）
        String fistTableName = tableNames.get(0);
        // 获取该表中的总记录数（行数）
        Long totalRows = getTotalRecords(fistTableName);
        // 获取该表中的总列数
        Long totalColumns = getTotalColumns(fistTableName);
        // 构建并返回Excel信息对象
        return ExcelPreviewResponse.ExcelInfo.builder()
                .fileId(fileId)
                .fileName(filesEntity.getFileName())
                .fileSize(filesEntity.getFileSize())
                .totalRows(totalRows)
                .totalColumns(totalColumns)
                .build();
    }

    /**
     * 根据文件ID和用户ID恢复文件数据
     *
     * @param fileId 文件ID，用于标识需要恢复的文件
     * @param userId 用户ID，用于验证用户权限
     * @return Boolean 恢复操作是否成功，true表示成功，false表示失败
     */
    @Override
    public Boolean restoreFileData(Long fileId, Long userId) {
        // 1. 查询文件信息
        // 通过文件ID查询文件实体信息，如果文件不存在或用户无权限则抛出异常
        FilesEntity filesEntity = filesMapper.selectById(fileId);
        if (filesEntity == null) {
            throw new IllegalArgumentException("文件不存在或者用户无权限");
        }

        // 2. 选择需要复原的mysql表
        // 根据文件ID获取所有需要恢复数据的表名列表
        List<String> tableNames = fileTableMappingService.getTableNamesByFileId(fileId);

        // 3. 获取原始excel文件
        // 从OSS存储系统下载原始Excel文件，如果文件无效则抛出异常
        MultipartFile file = downloadFileFromOss(filesEntity.getOssKey());
        if (file == null) {
            throw new RuntimeException("无法获取有效的文件");
        }
        // 遍历所有需要恢复的表
        for (int i = 0; i < tableNames.size(); i++) {
            String tableName = tableNames.get(i);
            // 4. 清空mysql表
            // 构建清空表的SQL语句并执行，为后续数据插入做准备
            String sql = "TRUNCATE TABLE " + tableName;
            jdbcTemplate.update(sql);

            // 5. 插入数据
            // 将Excel文件中的数据插入到对应的表中，i表示当前处理的是第几个表
            excel2TableService.insertData(tableName, file, i);
        }
        // 所有表数据恢复完成，返回成功
        return true;
    }

    /**
     * 删除文件的方法
     *
     * @param userId  用户ID，用于验证操作权限
     * @param request 文件删除请求对象，包含需要删除的文件信息，使用@Valid注解进行参数校验
     * @return 返回Boolean类型，表示删除操作是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteFile(Long userId, FileDeleteRequest request) {
        // 1. 遍历处理文件ID
        for (Long fileId : request.getFileIds()) {
            // 2. 判断权限
            FilesEntity filesEntity = filesMapper.selectByUserIdAndFileId(userId, fileId);
            if (filesEntity == null) {
                log.error("文件不存在或者无权限删除 {} {}", fileId, userId);
                continue;
            }
            // 3. 删除文件
            filesMapper.deleteById(fileId);
            // 4. 删除文件关联表
            fileTableMappingService.getTableNamesByFileId(fileId).forEach(tableName -> {
                jdbcTemplate.update("DROP TABLE IF EXISTS " + tableName);
                log.info("已成功删除表 {}", tableName);
            });
            // 5. 删除 file_table_mapping 表中对应记录
            fileTableMappingService.deleteByFileId(fileId);
            // 6. 删除 field_mappings 的记录
            fieldMappingService.deleteByFileId(fileId);
            // 7. 删除OSS文件
            ossService.deleteFile(filesEntity.getOssKey());
        }
        return true;
    }

    /**
     * 从OSS服务器下载文件并转换为MultipartFile对象
     *
     * @param ossKey OSS文件存储的键值，通常包含文件的完整路径
     * @return 返回一个MultipartFile对象，如果获取文件流失败则返回null
     */
    private MultipartFile downloadFileFromOss(String ossKey) {
        // 从OSS服务获取文件输入流
        InputStream inputStream = ossService.getOSSObject(ossKey).getObjectContent();
        // 检查输入流是否为空，为空则记录错误日志并返回null
        if (inputStream == null) {
            log.error("无法获取文件流 {}", ossKey);
            return null;
        }
        try {
            // 读取文件所有字节数据
            byte[] fileBytes = inputStream.readAllBytes();
            // 从ossKey中提取文件名
            String fileName = ossKey.substring(ossKey.lastIndexOf("/") + 1);
            // 创建并返回一个匿名MultipartFile对象
            return new MultipartFile() {
                @Override
                public String getName() {
                    return "file";
                }

                @Override
                public String getOriginalFilename() {
                    return fileName;
                }

                @Override
                public String getContentType() {
                    // 默认返回Excel文件类型
                    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                }

                @Override
                public boolean isEmpty() {
                    return fileBytes.length == 0;
                }

                @Override
                public long getSize() {
                    return fileBytes.length;
                }

                @Override
                public byte[] getBytes() throws IOException {
                    return fileBytes;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(fileBytes);
                }

                @Override
                public void transferTo(File dest) throws IOException, IllegalStateException {
                    Files.write(dest.toPath(), fileBytes);
                }
            };
        } catch (IOException e) {
            // 捕获IO异常并转换为运行时异常抛出
            throw new RuntimeException(e);
        }
    }


    /**
     * 获取指定表的列数
     *
     * @param tableName 表名
     * @return 返回表的列数（减去一列）
     */
    private Long getTotalColumns(String tableName) {
        // 构建SQL查询语句，用于描述表结构
        String sql = "describe " + tableName;
        // 执行查询并获取结果列表，每个元素是一个包含列信息的Map
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
        // 返回列数减1（通常describe会返回一行额外的信息(id列)，所以需要减去）
        return (long) (columns.size() - 1);
    }

    /**
     * 获取指定表中的总记录数
     *
     * @param tableName 需要查询记录数的表名
     * @return 返回表中记录的总数，以Long类型返回
     */
    private Long getTotalRecords(String tableName) {
        // 使用JdbcTemplate的queryForObject方法执行SQL查询
        // SQL语句为计算指定表中记录的数量
        return jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + tableName, Long.class);
    }


    /**
     * 将FilesEntity对象转换为FileInfoResponse对象
     * 该方法负责从FilesEntity中提取文件相关信息，并构建FileInfoResponse对象返回
     *
     * @param filesEntity 包含文件信息的实体对象
     * @return FileInfoResponse 包含格式化后文件信息的响应对象
     */
    private FileInfoResponse convert(FilesEntity filesEntity) {
        // 获取文件名
        String fileName = filesEntity.getFileName();
        // 使用Builder模式构建并返回FileInfoResponse对象
        return FileInfoResponse.builder()
                .fileId(filesEntity.getId())              // 设置文件ID
                .userId(filesEntity.getUserId())          // 设置用户ID
                .fileName(filesEntity.getFileName())      // 设置文件名
                .filePath(filesEntity.getFilePath())      // 设置文件路径
                .fileSize(filesEntity.getFileSize())      // 设置文件大小
                .fileUrl(filesEntity.getOssKey())         // 设置文件访问URL
                .ossKey(filesEntity.getOssKey())          // 设置OSS存储键
                .uploadStatus(filesEntity.getUploadStatus()) // 设置上传状态
                // 从文件名中提取文件扩展名
                .fileExtension(fileName.substring(fileName.lastIndexOf(".")))
                // 根据文件名获取文件内容类型
                .fileType(FileValidationUtil.getContentType(fileName))
                .build();    // 构建并返回FileInfoResponse对象
    }

    // 根据文件地址获取key
    private String getOssKey(String fileUrl) {
        String[] parts = fileUrl.split("/");
        if (parts.length >= 4) {
            StringBuilder key = new StringBuilder();
            for (int i = 3; i < parts.length; i++) {
                if (i > 3) {
                    key.append("/");
                }
                key.append(parts[i]);
            }
            return key.toString();
        }
        return "";
    }
}
