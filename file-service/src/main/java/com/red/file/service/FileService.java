package com.red.file.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.red.file.dto.request.ExcelPreviewRequest;
import com.red.file.dto.request.FileDeleteRequest;
import com.red.file.dto.request.FileListRequest;
import com.red.file.dto.request.FileUploadRequest;
import com.red.file.dto.response.ExcelPreviewResponse;
import com.red.file.dto.response.FileInfoResponse;
import com.red.file.dto.response.FileUploadResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

/**
 * 操作文件的接口
 */
public interface FileService {
    /**
     * 文件上传
     *
     * @param file
     * @param request
     * @param userId
     * @return
     */
    FileUploadResponse upload(MultipartFile file, FileUploadRequest request, Long userId);

    /**
     * 分页查询文件列表信息
     *
     * @param request 文件列表请求参数，包含查询条件等信息
     * @return IPage<FileInfoResponse> 返回分页后的文件信息响应结果
     */
    IPage<FileInfoResponse> list(FileListRequest request);


    /**
     * 下载文件的方法
     *
     * @param fileId   文件ID，用于标识需要下载的文件
     * @param response HTTP响应对象，用于将文件内容返回给客户端
     * @param userId   用户ID，用于验证用户是否有权限下载该文件
     */
    void downloadFile(Long fileId, HttpServletResponse response, Long userId);

    /**
     * 预览Excel文件的方法
     *
     * @param fileId  文件ID，用于标识需要预览的Excel文件
     * @param userId  用户ID，用于验证用户是否有权限预览该文件
     * @param request Excel预览请求参数，包含分页信息等
     * @return ExcelPreviewResponse 预览Excel文件的
     **/
    ExcelPreviewResponse previewExcel(Long fileId, Long userId, ExcelPreviewRequest request);

    /**
     * 根据文件ID获取Excel文件信息的方法
     *
     * @param fileId 文件ID
     * @return ExcelPreviewResponse.ExcelInfo 包含Excel文件信息的对象
     */
    ExcelPreviewResponse.ExcelInfo getExcelInfo(Long fileId);

    /**
     * 根据文件ID和用户ID恢复文件数据
     *
     * @param fileId 文件ID，用于标识需要恢复的文件
     * @param userId 用户ID，用于验证用户权限
     * @return Boolean 恢复操作是否成功，true表示成功，false表示失败
     */
    Boolean restoreFileData(Long fileId, Long userId);

/**
 * 删除文件的方法
 * @param userId 用户ID，用于验证操作权限
 * @param request 文件删除请求对象，包含需要删除的文件信息，使用@Valid注解进行参数校验
 * @return 返回Boolean类型，表示删除操作是否成功
 */
    Boolean deleteFile(Long userId, @Valid FileDeleteRequest request);
}
