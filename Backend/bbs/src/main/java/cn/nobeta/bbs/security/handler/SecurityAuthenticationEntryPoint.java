package cn.nobeta.bbs.security.handler;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.result.Result;
import lombok.extern.slf4j.Slf4j;


/**
 * 统一写 401 JSON
 */
@Component
@Slf4j
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        
        // 不记录查询参数，回调 URL 可能携带授权码。
        log.warn("BBS authentication required method={} path={} dispatcher={} exceptionType={}",
                request.getMethod(), request.getRequestURI().replaceAll("[\\r\\n\\t]", "_"),
                request.getDispatcherType(), authException.getClass().getSimpleName());
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> body = Result.fail(ResultCode.UNAUTHORIZED);
        response.getWriter().write(JSON.toJSONString(body));
    }
    

}
