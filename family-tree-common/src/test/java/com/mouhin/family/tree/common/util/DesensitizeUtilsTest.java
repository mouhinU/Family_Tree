package com.mouhin.family.tree.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 敏感信息脱敏工具单元测试：覆盖手机号 / 邮箱的正常脱敏、长度不足与空值边界。
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
class DesensitizeUtilsTest {

    @Test
    void maskPhoneKeepsPrefixAndSuffix() {
        assertEquals("159****0615", DesensitizeUtils.maskPhone("15926330615"));
    }

    @Test
    void maskPhoneFullyMasksShortValue() {
        assertEquals("****", DesensitizeUtils.maskPhone("1380000"));
    }

    @Test
    void maskPhonePassesThroughBlankValue() {
        assertNull(DesensitizeUtils.maskPhone(null));
        assertEquals("", DesensitizeUtils.maskPhone(""));
    }

    @Test
    void maskEmailMasksLocalPartOnly() {
        assertEquals("m****@example.com", DesensitizeUtils.maskEmail("mouhin@example.com"));
    }

    @Test
    void maskEmailKeepsSingleCharLocalPartMasked() {
        assertEquals("****@example.com", DesensitizeUtils.maskEmail("m@example.com"));
    }

    @Test
    void maskEmailMasksValueWithoutDomain() {
        assertEquals("****", DesensitizeUtils.maskEmail("not-an-email"));
    }

    @Test
    void maskEmailPassesThroughNull() {
        assertNull(DesensitizeUtils.maskEmail(null));
    }
}
