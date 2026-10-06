package cn.nobeta.bbs.module.auth.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import cn.nobeta.bbs.common.enums.RedisKeys;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.module.auth.service.AuthService;
import cn.nobeta.bbs.module.auth.service.SsoIdentityService;
import cn.nobeta.bbs.module.auth.vo.TokenVO;
import cn.nobeta.bbs.security.util.TokenProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

/** Auth 完成账号认证，BBS 只签发、刷新及撤销自己的业务令牌。 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final TokenProvider tokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SsoIdentityService identities;

    @Override
    public TokenVO issue(UserAuthInfo loginUser) {
        String accessToken = tokenProvider.generateAccessToken(loginUser);
        String refreshToken = tokenProvider.generateRefreshToken(loginUser);
        Long userId = loginUser.getUser().getId();
        Duration ttl = Duration.between(Instant.now(), tokenProvider.getExpiration(refreshToken).toInstant());
        redisTemplate.opsForValue().set(RedisKeys.LOGIN_USER.getFullKey(userId), loginUser, ttl);
        String key = RedisKeys.REFRESH_TOKEN.getFullKey(userId);
        redisTemplate.opsForHash().put(key, tokenProvider.getJti(refreshToken), refreshToken);
        redisTemplate.expire(key, ttl);
        return TokenVO.builder().accessToken(accessToken).refreshToken(refreshToken)
                .expireIn(tokenProvider.getExpiration(accessToken)).build();
    }

    @Override
    public TokenVO refresh(String refreshToken) {
        try {
            if (!tokenProvider.isRefreshToken(refreshToken)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "令牌类型错误");
            }
            Long userId = tokenProvider.getUserId(refreshToken);
            String key = RedisKeys.REFRESH_TOKEN.getFullKey(userId);
            String jti = tokenProvider.getJti(refreshToken);
            Object cached = redisTemplate.opsForHash().get(key, jti);
            if (!refreshToken.equals(cached)
                    || !Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.LOGIN_USER.getFullKey(userId)))) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已失效");
            }
            // 每次刷新重新读取社区状态和权限；不用认证中心的角色覆盖社区权限。
            UserAuthInfo local = identities.load(userId);
            if (redisTemplate.opsForHash().delete(key, jti) != 1L) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌已被使用");
            }
            return issue(local);
        } catch (JwtException | IllegalArgumentException invalid) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌已失效");
        }
    }

    @Override
    public void logout(Long userId, String accessToken) {
        try {
            if (!tokenProvider.isAccessToken(accessToken) || !userId.equals(tokenProvider.getUserId(accessToken))) return;
            Duration ttl = Duration.between(Instant.now(), tokenProvider.getExpiration(accessToken).toInstant());
            if (!ttl.isNegative() && !ttl.isZero()) {
                redisTemplate.opsForValue().set(RedisKeys.TOKEN_BLACK.getFullKey(tokenProvider.getJti(accessToken)), "1", ttl);
            }
            // 保留原有按用户结束全部 BBS 登录的语义，不注销 Auth 或其他应用。
            redisTemplate.delete(List.of(RedisKeys.REFRESH_TOKEN.getFullKey(userId), RedisKeys.LOGIN_USER.getFullKey(userId)));
        } catch (JwtException | IllegalArgumentException invalid) {
            // 无效或过期凭据无需再次撤销。
        }
    }
}
