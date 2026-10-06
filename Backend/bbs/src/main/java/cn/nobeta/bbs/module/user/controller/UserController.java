package cn.nobeta.bbs.module.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.nobeta.bbs.common.annotation.AuditLog;
import cn.nobeta.bbs.common.annotation.RateLimit;
import cn.nobeta.bbs.common.enums.Scene;
import cn.nobeta.bbs.common.result.Result;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.module.user.dto.UserProfileDTO;
import cn.nobeta.bbs.module.user.service.UserService;
import cn.nobeta.bbs.module.user.vo.AvatarVO;
import cn.nobeta.bbs.module.user.vo.UserInfoVO;
import cn.nobeta.bbs.module.user.vo.UserProfileVO;

/**
 * 用户相关接口
 */
@RequestMapping("/api/users")
@RestController
@Slf4j
@Tag(name = "用户相关接口", description = "个人资料页接口")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前用户个人资料
     * @param loginUser
     * @return
     */
    @RateLimit(scene = Scene.READ)
    @AuditLog(message = "获取当前用户个人资料", data = "{'username': #p0.username}")
    @GetMapping("/me")
    public Result<UserProfileVO> getCurrentUserProfile(@AuthenticationPrincipal UserAuthInfo loginUser) {

        Long userId = loginUser.getUser().getId();

        UserProfileVO userProfileVO = userService.queryUserProfileById(userId);

        return Result.success(userProfileVO);

    }

    /**
     * 查询其他用户个人信息
     * @param id 被查询用户id
     * @return
     */
    @RateLimit(scene = Scene.READ)
    @AuditLog(message = "查询用户个人信息", data = "{'userId': #p0}")
    @GetMapping("/{id}")
    public Result<UserInfoVO> getUserInfo(@PathVariable Long id) {
        
        UserInfoVO userInfoVO = userService.queryUserInfoById(id);

        return Result.success(userInfoVO);
    }

    /**
     * 当前用户更新个人资料
     * @param loginUser
     * @param userProfileDTO
     * @return
     */
    @RateLimit(scene = Scene.WRITE)
    @AuditLog(message = "更新当前用户个人资料", data = "{'nickname': #p1.nickname, 'sex': #p1.sex, 'race': #p1.race}")
    @PutMapping("/me")
    public Result<Void> editCurrentUserProfile(
        @AuthenticationPrincipal UserAuthInfo loginUser,
        @Valid @RequestBody UserProfileDTO userProfileDTO
    ) {

        Long userId = loginUser.getUser().getId();

        userService.editUserProfile(userId, userProfileDTO);

        return Result.success();
    }

    /**
     * 用户头像更新接口
     * @param loginUser
     * @param file
     * @return
     */
    @RateLimit(scene = Scene.WRITE)
    @AuditLog(message = "更新当前用户头像", data = "{'username': #p0.username, 'filename': #p1.originalFilename, 'size': #p1.size}")
    @PostMapping("/me/avatar")
    public Result<AvatarVO> editUserAvatar(
        @AuthenticationPrincipal UserAuthInfo loginUser,
        @NotNull(message = "文件不能为空")
        @RequestParam MultipartFile file
    ) {

        Long userId = loginUser.getUser().getId();

        AvatarVO avatarVO = userService.editUserAvatar(userId, file);

        return Result.success(avatarVO);

    }

}
