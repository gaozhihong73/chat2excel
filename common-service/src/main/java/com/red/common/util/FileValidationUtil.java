package com.red.common.util;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * 文件校验的工具类
 */
@Component
public class FileValidationUtil {
    // 文件扩展名的支持列表
    private static final List<String> SUPPORT_FILE_EXTENSIONS = Arrays.asList(".xls", ".xlsx");

    // 文件类型的支持列表
    private static final List<String> SUPPORT_FILE_TYPE = Arrays.asList(
            "application/vnd.ms-excel", // .xls
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
            "multipart/form-data"
    );

    /**
     * 根据文件名获取文件的Content-Type
     *
     * @param fileName 文件名，包含文件扩展名
     * @return 对应文件的Content-Type字符串，如果文件类型不支持则返回空字符串
     */
    public static String getContentType(String fileName) {
        // 获取文件扩展名
        String extension = fileName.substring(fileName.lastIndexOf("."));
        // 根据不同的扩展名返回对应的Content-Type
        switch (extension) {
            case ".xls":
                return "application/vnd.ms-excel";
            case ".xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default:
                return "";
        }
    }

    // 获取文件扩展名
    private static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf("."));
    }

    // 校验文件格式是否支持
    public static boolean vavalidateFileFormat(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // 检查文件扩展名
        String extension = getFileExtension(file.getOriginalFilename());
        if (!SUPPORT_FILE_EXTENSIONS.contains(extension)) {
            return false;
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (!SUPPORT_FILE_TYPE.contains(contentType)) {
            return false;
        }

        return true;
    }

    /**
     * 判断文件大小 是否超过50MB
     *
     * @param file
     * @return
     */
    public static boolean ifOutOfLarge(MultipartFile file) {
        return file.getSize() <= 50 * 1024 * 1024;
    }


}
