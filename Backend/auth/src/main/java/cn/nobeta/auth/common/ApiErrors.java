package cn.nobeta.auth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Business controllers only. OAuth endpoints retain their framework error handlers. */
@RestControllerAdvice(basePackages = "cn.nobeta.auth.module")
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalidRequest(Exception exception) {
        log.warn("Controller request validation failed exceptionType={}", exception.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicate(DuplicateKeyException exception) {
        log.warn("Controller request rejected reason=duplicate_identifier");
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The identifier already exists");
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ProblemDetail business(org.springframework.web.server.ResponseStatusException exception) {
        log.warn("Controller business request rejected status={}", exception.getStatusCode().value());
        return exception.getBody();
    }
}
