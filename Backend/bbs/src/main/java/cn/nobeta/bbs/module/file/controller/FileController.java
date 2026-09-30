package cn.nobeta.bbs.module.file.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import cn.nobeta.bbs.common.annotation.AuditLog;
import cn.nobeta.bbs.common.annotation.RateLimit;
import cn.nobeta.bbs.common.enums.Scene;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.common.result.Result;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.module.file.entity.ImagePurpose;
import cn.nobeta.bbs.module.file.service.FileService;
import cn.nobeta.bbs.module.file.vo.ImageUploadVO;
import cn.nobeta.bbs.module.user.vo.AvatarVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "文件上传接口")
public class FileController {
    private final FileService fileService;

    @RateLimit(scene = Scene.WRITE)
    @AuditLog(message = "用户上传头像", data = "{'size': #p1.size}")
    @PostMapping("/uploadAvatar")
    public Result<AvatarVO> uploadAvatar(@AuthenticationPrincipal UserAuthInfo user,
        @RequestParam("file") MultipartFile file) {
        return Result.success(AvatarVO.builder().avatarUrl(
            fileService.uploadManagedImage(user.getUser().getId(), ImagePurpose.AVATAR, file)).build());
    }

    @RateLimit(scene = Scene.WRITE)
    @AuditLog(message = "用户上传资料图片", data = "{'purpose': #p1, 'size': #p2.size}")
    @PostMapping("/images")
    public Result<ImageUploadVO> uploadManagedImage(@AuthenticationPrincipal UserAuthInfo user,
        @RequestParam("purpose") ImagePurpose purpose, @RequestParam("file") MultipartFile file) {
        if (purpose == ImagePurpose.AVATAR) {
            throw new BusinessException(ResultCode.ILLEGAL_ARGUMENT, "头像请使用头像上传接口");
        }
        return Result.success(ImageUploadVO.builder().purpose(purpose).url(
            fileService.uploadManagedImage(user.getUser().getId(), purpose, file)).build());
    }

    @RateLimit(scene = Scene.WRITE)
    @AuditLog(message = "用户上传正文图片", data = "{'size': #p0.size}")
    @PostMapping("/uploadImage")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file));
    }
}
