package com.mouhin.family.tree.web.config;

import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        logger.warn("Business exception: code={} message={}", e.getErrorCode(), e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Illegal argument: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return Result.fail(500, "系统内部错误");
    }
}
