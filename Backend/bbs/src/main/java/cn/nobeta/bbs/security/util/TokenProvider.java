package cn.nobeta.bbs.security.util;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import cn.nobeta.bbs.config.properties.JwtProperties;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;

@Component
@RequiredArgsConstructor
public class TokenProvider {

    /** Jwt 配置类 */
    private final JwtProperties jwtProperties;

    /** 访问令牌过期时间(秒) */
    private long accessTokenExpire;
    /** 刷新令牌过期时间(秒) */
    private long refreshTokenExpire;

    /** 全局签名密钥 */
    private SecretKey secretKey;

    // 容器属性注入完成后初始化签名密钥和配置
    @PostConstruct
    public void init() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("BBS JWT 签名密钥必须显式配置且不少于32个字符");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);

        accessTokenExpire = jwtProperties.getAccessTokenExpire();
        refreshTokenExpire = jwtProperties.getRefreshTokenExpire();
        if (accessTokenExpire <= 0 || refreshTokenExpire <= 0) {
            throw new IllegalStateException("BBS 令牌有效期必须是正整数，单位为秒");
        }
    }

    /** 生成 Access Token */
    public String generateAccessToken(UserAuthInfo loginUser) {
        return buildToken(loginUser, accessTokenExpire, "access");
    }
    /** 生成 Refresh Token */
    public String generateRefreshToken(UserAuthInfo loginUser) {
        return buildToken(loginUser, refreshTokenExpire, "refresh");
    }

    private String buildToken(UserAuthInfo loginUser, long expireSeconds, String type) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .subject(loginUser.getUser().getId().toString())
                .claim("username", loginUser.getUser().getUsername())
                .claim("roles", loginUser.getRoles())
                .claim("type", type)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expire)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析 Token，获取 Claims (包含所有声明) **/
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        // Token 无效/过期抛出 JwtException 子类
    }

    /** 从 Token 中提取用户 ID **/
    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /** 从 Token 中解析 jti (JWT 唯一 Id) **/
    public String getJti(String token) {
        return parseToken(token).getId();
    }

    /** 获取 Token 过期时间 **/
    public Date getExpiration(String token) {
        return parseToken(token).getExpiration();
    }

    /** 校验 Token 是否生效（签名正确 + 未过期） **/
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            throw e; // 过期单独抛出，由 Filter 返回特定错误码
        } catch (JwtException | IllegalArgumentException e) {
            return false; // 签名错误、格式错误等
        }
    }

    /** 判断是否为 AccessToken **/
    public boolean isAccessToken(String token) {
        return "access".equals(parseToken(token).get("type", String.class));
    }

    /** 判断是否为 RefreshToken **/
    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseToken(token).get("type", String.class));
    }

}
