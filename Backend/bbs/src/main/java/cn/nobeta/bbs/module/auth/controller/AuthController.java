package cn.nobeta.bbs.module.auth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.nobeta.bbs.common.annotation.AuditLog;
import cn.nobeta.bbs.common.annotation.RateLimit;
import cn.nobeta.bbs.common.result.Result;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.module.auth.service.AuthService;
import cn.nobeta.bbs.module.auth.vo.TokenVO;

/**
 * BBS 本地业务令牌的刷新和退出接口
 */
@RequestMapping("/api/auth")
@RestController
@Validated
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 刷新令牌
     * @param refreshToken
     * @return
     */
    @RateLimit(capacity = 5, refill = 1)
    @AuditLog(message = "用户刷新令牌")
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@NotBlank @RequestParam String refreshToken) {

        TokenVO tokenVO = authService.refresh(refreshToken);

        return Result.success(tokenVO);
    }

    /**
     * 登出接口
     * @param loginUser
     * @param refreshToken
     * @return
     */
    @AuditLog(message = "用户登出", data = "{'username': #p0.getUsername()}")
    @PostMapping("/logout")
    public Result<Void> logout(
        @AuthenticationPrincipal UserAuthInfo loginUser,
        @RequestHeader("Authorization") String authHeader
    ) {
        String accessToken = authHeader.substring(7);
        Long userId = loginUser.getUser().getId();

        authService.logout(userId, accessToken);

        return Result.success();
    }
}
