package com.red.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.red.common.annotation.LogOperation;
import com.red.common.util.JwtUtil;
import com.red.common.util.Result;
import com.red.file.dto.request.ExcelPreviewRequest;
import com.red.file.dto.request.FileDeleteRequest;
import com.red.file.dto.request.FileListRequest;
import com.red.file.dto.request.FileUploadRequest;
import com.red.file.dto.response.ExcelPreviewResponse;
import com.red.file.dto.response.FileInfoResponse;
import com.red.file.dto.response.FileUploadResponse;
import com.red.file.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/files")
public class FileController {
    @Autowired
    private FileService fileService;

    @Autowired
    private JwtUtil jwtUtil;


    /**
     * 单个文件上传接口
     */
    @PostMapping("/upload/single")
    @LogOperation("文件上传")
    public Result<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String authorization,
            @Valid FileUploadRequest request
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }

        FileUploadResponse response = fileService.upload(file, request, userId);
        return Result.success("文件上传成功", response);
    }


    /**
     * 获取文件列表接口
     * 通过Authorization请求头获取用户ID，验证令牌有效性后查询用户文件列表
     *
     * @param authorization 认证令牌，从请求头中获取
     * @param request       文件列表查询请求参数，包含分页、排序等信息
     * @return 返回文件列表查询结果，包含分页信息和文件数据
     */
    @GetMapping("/list")
    @LogOperation("文件列表查询")  // 记录操作日志，标记为"文件列表查询"操作
    public Result<IPage<FileInfoResponse>> list(
            @RequestHeader("Authorization") String authorization,  // 从请求头获取认证令牌
            @Valid FileListRequest request  // 验证文件列表请求参数的有效性
    ) {
        // 通过JWT令牌解析获取用户ID
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        // 如果用户ID为空，说明令牌无效，返回错误响应
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }

        // 设置查询请求中的用户ID
        request.setUserId(userId);
        // 调用服务层获取文件列表
        IPage<FileInfoResponse> response = fileService.list(request);
        // 返回成功响应，包含查询结果
        return Result.success("文件列表查询成功", response);
    }

    /**
     * 处理文件下载请求的接口方法
     * 使用@GetMapping注解映射下载路径
     *
     * @param authorization 请求头中的Authorization信息，用于用户身份验证
     * @param fileId        要下载的文件ID
     * @param response      HTTP响应对象，用于返回文件流
     * @LogOperation用于记录操作日志
     */
    @GetMapping("/download")
    @LogOperation("文件下载")
    public void downloadFile(@RequestHeader("Authorization") String authorization,
                             @RequestParam("fileId") Long fileId,
                             HttpServletResponse response) {
        // 通过JWT令牌获取用户ID
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        // 验证用户身份
        if (userId == null) {
            // 设置响应状态为未授权
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            // 设置响应内容类型为JSON
            response.setContentType("application/json;charset=UTF-8");
            try {
                // 返回错误信息
                response.getWriter().write("{\"code\":401,\"message\":\"无效的令牌\"}");
                return;
            } catch (IOException e) {
                // 抛出运行时异常
                throw new RuntimeException(e);
            }
        }
        // 调用服务层方法处理文件下载
        fileService.downloadFile(fileId, response, userId);
    }


    @GetMapping("excel/preview/{fileId}")
    @LogOperation("文件预览")
    public Result<ExcelPreviewResponse> previewExcel(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String authorization,
            @Valid ExcelPreviewRequest request) {

        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }

        ExcelPreviewResponse response = fileService.previewExcel(fileId, userId, request);
        return Result.success("Excel文件预览成功", response);
    }

    /**
     * 根据文件ID获取Excel文件信息的接口
     *
     * @param fileId        文件ID，路径变量
     * @param authorization 认证令牌，请求头信息
     * @return 返回文件信息查询结果，包含Excel详细信息
     */
    @GetMapping("/excel/info/{fileId}")
    @LogOperation("Excel文件信息查询")  // 记录操作日志，标记为"文件信息查询"操作
    public Result<ExcelPreviewResponse.ExcelInfo> getExcelInfo(
            @PathVariable Long fileId,     // 从URL路径中获取的文件ID
            @RequestHeader("Authorization") String authorization) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("Excel文件信息查询成功", fileService.getExcelInfo(fileId));
    }

    // 一键复原文件
    @PostMapping("/restore/{fileId}")
    @LogOperation("一键复原excel数据")
    public Result<Boolean> restoreFileData(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("文件复原成功", fileService.restoreFileData(fileId, userId));
    }

    @DeleteMapping("/delete")
    @LogOperation("文件删除")
    public Result<Boolean> deleteFile(
            @RequestHeader("Authorization") String authorization,
            @RequestBody @Valid FileDeleteRequest request) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("文件删除成功", fileService.deleteFile(userId, request));
    }
}
