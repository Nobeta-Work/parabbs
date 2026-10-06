package cn.nobeta.bbs.security.filter;

import java.io.IOException;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import cn.nobeta.bbs.common.enums.RedisKeys;
import cn.nobeta.bbs.module.auth.dto.UserAuthInfo;
import cn.nobeta.bbs.security.util.TokenProvider;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final TokenProvider tokenProvider;
	private final RedisTemplate<String, Object> redisTemplate;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
	
		// 1. 从 request 中提取 token
		String token = resolveToken(request);
	
		if (token == null) {
			// token 为空
			chain.doFilter(request, response);
			return;
		}

		try {
			// 提取 claims
			Claims claims = tokenProvider.parseToken(token);
            // 刷新令牌不能当作访问令牌用于业务接口。
            if (!"access".equals(claims.get("type", String.class))) {
                chain.doFilter(request, response);
                return;
            }

			// 黑名单检查 jti 是否拉黑
			if (isBlacklisted(claims.getId())) {
				chain.doFilter(request, response);
				return;
			}
			
			// 从 Redis 加载用户
			Long userId = Long.parseLong(claims.getSubject());
			UserAuthInfo loginUser = loadUserFromRedis(userId);
			if (loginUser == null || !loginUser.isEnabled()) {
				chain.doFilter(request, response);
				return;
			}

			// 设置认证
			UsernamePasswordAuthenticationToken authentication = 
				new UsernamePasswordAuthenticationToken(
					loginUser, null, loginUser.getAuthorities()
				);
			authentication.setDetails(
				new WebAuthenticationDetailsSource().buildDetails(request)
			);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (JwtException | IllegalArgumentException e) {
			// Token 过期、签名错误，放行
		}
	
		chain.doFilter(request, response);
	}

	/** ====== 一些功能方法 ====== **/
	/** 从 Authorization Header 提取 Token **/
	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
			return header.substring(7);
		}
		return null;
	}

	/** 检查 Token 是否在黑名单 **/
	private boolean isBlacklisted(String jti) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.TOKEN_BLACK.getFullKey(jti)));
	}

	/** 从 Redis 加载用户信息 **/
	private UserAuthInfo loadUserFromRedis(Long userId) {
		return (UserAuthInfo) redisTemplate.opsForValue().get(RedisKeys.LOGIN_USER.getFullKey(userId.toString()));
	}

	
}
