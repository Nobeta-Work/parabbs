package cn.nobeta.bbs.handler;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import cn.nobeta.bbs.common.enums.ResultCode;
import cn.nobeta.bbs.common.exception.BaseException;
import cn.nobeta.bbs.common.result.Result;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler(BaseException.class)
    public Result<Void> exceptionHandler(BaseException ex) {
        log.warn("异常：{}<", ex.getMessage());
        return Result.fail(ex.getResultCode(), ex.getMessage());
    }

    /**
     * 捕获参数校验异常
     * @param ex
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidateException(MethodArgumentNotValidException ex) {
        return Result.fail(ResultCode.ILLEGAL_ARGUMENT, "请求参数校验失败");
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    public Result<Void> handleMalformedRequest(Exception ex) {
        return Result.fail(ResultCode.ILLEGAL_ARGUMENT, "请求字段缺失或格式错误");
    }

    /**
     * 捕获全局异常
     * @param e
     * @return
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未知异常", e);
        return Result.fail(ResultCode.FAIL);
    }
}
