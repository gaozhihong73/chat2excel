package com.red.file.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 文件预览响应
 * 用于封装Excel文件预览时的相关信息
 */
@Data
@Builder
public class ExcelPreviewResponse {

    private ExcelInfo excelInfo;    // Excel文件的基本信息对象

    private List<SheetInfo> sheets;  // Excel文件中所有工作表的信息列表

    private Integer currentSheetIndex; // 当前正在预览的工作表索引

    private List<ColumnHeader> headers; // 当前工作表的列头信息列表

    private List<Map<String, Object>> dataRows; // 当前工作表的数据行列表

    private PaginationInfo paginationInfo; // 分页信息对象


    /**
     * Excel文件信息内部类
     * 用于存储Excel文件的基本信息
     */
    @Data
    @Builder
    public static class ExcelInfo {
        // 文件ID
        private Long fileId;

        // 文件名
        private String fileName;

        // 文件大小
        private Long fileSize;

        // 总行数
        private Long totalRows;

        // 总列数
        private Long totalColumns;
    }

    /**
     * 工作表信息内部类
     * 用于存储Excel文件中单个工作表的信息
     */
    @Data
    @Builder
    public static class SheetInfo {
        // sheet索引
        private Integer sheetIndex;

        // sheet名称
        private String sheetName;

        // 对应的mysql表名
        private String tableName;

        // 总行数
        private Long totalRows;

        // 总列数
        private Long totalColumns;
    }

    /**
     * 列头信息内部类
     * 用于存储Excel列的标题信息
     */
    @Data
    @Builder
    public static class ColumnHeader {
        // 数据库字段名
        private String dbFieldName;

        // 原始excel列名
        private String originalHeader;
    }

    /**
     * 分页信息内部类
     * 用于存储分页相关的信息
     */
    @Data
    @Builder
    public static class PaginationInfo {
        // 当前页
        private Integer currentPage;

        // 每页的大小
        private Integer pageSize;

        // 总页数
        private Long totalPages;

        // 总记录数
        private Long totalRecords;

        // 是否有下一页
        private Boolean hasNext;

        // 是否有上一页
        private Boolean hasPrevious;
    }
}
