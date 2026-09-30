package cn.nobeta.bbs.module.auth.service.impl;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import cn.nobeta.bbs.common.enums.RedisKeys;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.exception.BusinessException;
import cn.nobeta.bbs.module.auth.dto.LoginDTO;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.module.auth.mapper.AuthMapper;
import cn.nobeta.bbs.module.auth.dto.RegisterDTO;
import cn.nobeta.bbs.module.auth.service.AuthService;
import cn.nobeta.bbs.module.auth.vo.TokenVO;
import cn.nobeta.bbs.module.user.entity.User;
import cn.nobeta.bbs.module.user.entity.UserProfile;
import cn.nobeta.bbs.module.user.mapper.UserMapper;
import cn.nobeta.bbs.security.util.TokenProvider;


@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenProvider tokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    private final AuthMapper authMapper;


    /**
     * 登陆接口
     */
    @Override
    public TokenVO login(LoginDTO loginDTO) {

        // 1. 委托 SpringSecurity 认证
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword())
        );

        // 2. 提取 loginUser
        UserAuthInfo loginUser = (UserAuthInfo) authentication.getPrincipal();
        Long userId = loginUser.getUser().getId();

        // !: 兼容功能：覆写密码
        userMapper.updateUserPassword(userId, loginUser.getPassword());
        
        // 3. 生成双 Token
        String accessToken = tokenProvider.generateAccessToken(loginUser);
        String refreshToken = tokenProvider.generateRefreshToken(loginUser);
        Date expireIn = tokenProvider.getExpiration(accessToken);

        // 4. 缓存用户信息到 Redis
        Duration ttl = Duration.ofSeconds(RedisKeys.REFRESH_TOKEN.getDefaultTtl());
        redisTemplate.opsForValue().set(
            RedisKeys.LOGIN_USER.getFullKey(userId),
            loginUser, ttl
        );

        // 5. 缓存 RefreshToken
        String key = RedisKeys.REFRESH_TOKEN.getFullKey(userId);
        redisTemplate.opsForHash().put(
            key,
            tokenProvider.getJti(refreshToken),
            refreshToken
        );
        redisTemplate.expire(key, ttl);

        return TokenVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expireIn(expireIn)
                .build();

    }


    /**
     * 注册接口
     */
    @Override
    @Transactional
    public void register(RegisterDTO registerDTO) {
        // 1. 加密密码
        String passwordHash = passwordEncoder.encode(registerDTO.getPassword());

        // 2. 注册用户
        // 2.1 新建用户认证信息
        User user = User.builder()
                .username(registerDTO.getUsername())
                .password(passwordHash)
                .status(1)
                .build();
        
        try {
            authMapper.insertUser(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.USERNAME_DUPLICATE);
        }

        try {
            userMapper.insertUserProfile(UserProfile.builder()
                .userId(user.getId())
                .nickname(registerDTO.getNickname())
                .sex(registerDTO.getSex())
                .race(registerDTO.getRace())
                .signature("")
                .build());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.NICKNAME_DUPLICATE);
        }

        // 2.2 创建默认角色配置
        int row = authMapper.insertUserDefaultRole(user.getId());
        if (row != 1) {
            throw new BusinessException(ResultCode.FAIL, "用户默认角色配置失败");
        }
    }


    /**
     * 刷新令牌
     */
    @Override
    public TokenVO refresh(String refreshToken) {
        // 1. 验证令牌签名、有效期
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Token 失效");
        }

        // 2. 校验令牌类型
        if (!tokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ResultCode.FAIL, "Token 类型错误");
        }

        // 3. 查看 Redis 令牌、用户缓存
        Long userId = tokenProvider.getUserId(refreshToken);
        String refreshKey = RedisKeys.REFRESH_TOKEN.getFullKey(userId);

        String cacheRefreshToken = (String) redisTemplate.opsForHash().get(
            refreshKey,
            tokenProvider.getJti(refreshToken)
        );
        if (cacheRefreshToken == null || !cacheRefreshToken.equals(refreshToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Token 失效");
        }

        UserAuthInfo loginUser = (UserAuthInfo) redisTemplate.opsForValue().get(
            RedisKeys.LOGIN_USER.getFullKey(userId)
        );
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户信息不存在");
        }

        // 4. 重新生成令牌
        String newAccessToken = tokenProvider.generateAccessToken(loginUser);
        String newRefreshToken = tokenProvider.generateRefreshToken(loginUser);
        Date expireIn = tokenProvider.getExpiration(newAccessToken);

        // 5. 更新 Redis 令牌、用户缓存
        Duration ttl = Duration.ofSeconds(RedisKeys.REFRESH_TOKEN.getDefaultTtl());
        redisTemplate.opsForHash().delete(
            refreshKey,
            tokenProvider.getJti(refreshToken)
        );
        redisTemplate.opsForHash().put(
            refreshKey,
            tokenProvider.getJti(newRefreshToken),
            newRefreshToken
        );
        redisTemplate.expire(refreshKey, ttl);
        redisTemplate.opsForValue().set(
            RedisKeys.LOGIN_USER.getFullKey(userId),
            loginUser, ttl
        );

        // 6. 返回结果
        return TokenVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expireIn(expireIn)
                .build();
    }


    /**
     * 登出接口
     * @return 必须返回，登出操作一定成功
     */
    @Override
    public void logout(Long userId, String accessToken) {
        // 1. 校验令牌签名
        if (!tokenProvider.validateToken(accessToken)) { return; }

        // 2. 校验令牌类型
        if (!tokenProvider.isAccessToken(accessToken)) { return; }

        // 3. 校验用户一致
        if (!userId.equals(tokenProvider.getUserId(accessToken))) { return; }

        // 4. 拉黑业务令牌 jti
        Duration accessTtl = Duration.ofSeconds(RedisKeys.TOKEN_BLACK.getDefaultTtl());

        String jti = tokenProvider.getJti(accessToken);
        redisTemplate.opsForValue().set(
            RedisKeys.TOKEN_BLACK.getFullKey(jti),
            "1", accessTtl
        );

        // 5. 删除 refreshToken 和 loginUser 缓存
        redisTemplate.delete(
            List.of(
                RedisKeys.REFRESH_TOKEN.getFullKey(userId),
                RedisKeys.LOGIN_USER.getFullKey(userId)
            )
        );
    }

    

}
