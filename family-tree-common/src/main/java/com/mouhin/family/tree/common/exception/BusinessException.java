package com.mouhin.family.tree.common.exception;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
    }

    public BusinessException(String errorMessage) {
        this("BIZ_ERROR", errorMessage);
    }
}
