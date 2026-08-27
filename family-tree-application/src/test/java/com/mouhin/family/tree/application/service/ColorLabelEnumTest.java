package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.enums.ColorLabelEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 颜色标注枚举校验逻辑单元测试
 *
 * @author Family-Tree
 * @date 2026-07-31
 */
class ColorLabelEnumTest {

    @Test
    void validateCodeAcceptsAllLegalCodes() {
        for (ColorLabelEnum value : ColorLabelEnum.values()) {
            assertDoesNotThrow(() -> ColorLabelEnum.validateCode(value.getCode()));
        }
    }

    @Test
    void validateCodeRejectsIllegalCode() {
        assertThrows(BusinessException.class, () -> ColorLabelEnum.validateCode("invalid_xxx"));
        assertThrows(BusinessException.class, () -> ColorLabelEnum.validateCode(null));
    }

    @Test
    void fromCodeFallsBackToDefaultForIllegalCode() {
        assertEquals(ColorLabelEnum.DEFAULT, ColorLabelEnum.fromCode("invalid_xxx"));
        assertEquals(ColorLabelEnum.PATERNAL, ColorLabelEnum.fromCode("paternal"));
    }
}
