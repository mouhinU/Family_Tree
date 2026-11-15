package com.mouhin.family.tree.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 农历↔阳历换算工具单元测试。
 * 覆盖：阳历转农历、宽松解析、农历转阳历（含闰月）、边界与非法输入。
 *
 * @author Family-Tree
 * @date 2026-11-14
 */
class LunarSolarUtilTest {

    @Test
    void solarToLunarProducesGanzhiYearMonthDay() {
        assertEquals("丙寅年四月二十", LunarSolarUtil.solarToLunar(LocalDate.of(1986, 5, 28)));
        assertEquals("己亥年腊月十一", LunarSolarUtil.solarToLunar(LocalDate.of(2020, 1, 5)));
    }

    @Test
    void solarToLunarReturnsNullForNull() {
        assertNull(LunarSolarUtil.solarToLunar(null));
    }

    @Test
    void solarTextToLunarParsesAndFormats() {
        assertEquals("丙寅年四月二十", LunarSolarUtil.solarTextToLunar("1986-05-28"));
        assertEquals("丙寅年四月二十", LunarSolarUtil.solarTextToLunar("  1986-05-28  "));
    }

    @Test
    void solarTextToLunarReturnsNullForBlank() {
        assertNull(LunarSolarUtil.solarTextToLunar(null));
        assertNull(LunarSolarUtil.solarTextToLunar(""));
        assertNull(LunarSolarUtil.solarTextToLunar("   "));
    }

    @Test
    void solarTextToLunarThrowsOnMalformed() {
        assertThrows(RuntimeException.class, () -> LunarSolarUtil.solarTextToLunar("不是日期"));
    }

    @Test
    void solarTextToLunarOrNullSwallowsMalformed() {
        assertNull(LunarSolarUtil.solarTextToLunarOrNull("不是日期"));
        assertNull(LunarSolarUtil.solarTextToLunarOrNull("2020-13-45"));
        assertEquals("丙寅年四月二十", LunarSolarUtil.solarTextToLunarOrNull("1986-05-28"));
    }

    @Test
    void solarToLunarYearKeepsSolarYearAfterSpringFestival() {
        assertEquals(1986, LunarSolarUtil.solarToLunarYear(LocalDate.of(1986, 5, 28)));
        assertEquals(1990, LunarSolarUtil.solarToLunarYear(LocalDate.of(1990, 11, 23)));
    }

    @Test
    void solarToLunarYearFallsBackToPreviousYearBeforeSpringFestival() {
        // 1991 年春节为 1991-02-15，1 月出生仍属庚午（1990）年
        assertEquals(1990, LunarSolarUtil.solarToLunarYear(LocalDate.of(1991, 1, 8)));
        // 仅有年份的占位日期 YYYY-01-01 一律落在上一个农历年
        assertEquals(1949, LunarSolarUtil.solarToLunarYear(LocalDate.of(1950, 1, 1)));
    }

    @Test
    void solarToLunarYearReturnsNullForNull() {
        assertNull(LunarSolarUtil.solarToLunarYear(null));
    }

    @Test
    void lunarToSolarHandlesNormalMonth() {
        assertEquals(LocalDate.of(1986, 5, 28), LunarSolarUtil.lunarToSolar(1986, 4, 20, false));
    }

    @Test
    void lunarToSolarHandlesLeapMonth() {
        // 2017 年闰六月初一 → 阳历 2017-07-23
        assertEquals(LocalDate.of(2017, 7, 23), LunarSolarUtil.lunarToSolar(2017, 6, 1, true));
        // 同月非闰六月初一 → 阳历 2017-06-24
        assertEquals(LocalDate.of(2017, 6, 24), LunarSolarUtil.lunarToSolar(2017, 6, 1, false));
    }

    @Test
    void roundTripSolarLunarSolarIsStable() {
        LocalDate solar = LocalDate.of(2023, 3, 18);
        com.nlf.calendar.Lunar lunar = com.nlf.calendar.Solar.fromYmd(
                solar.getYear(), solar.getMonthValue(), solar.getDayOfMonth()).getLunar();
        LocalDate back = LunarSolarUtil.lunarToSolar(
                lunar.getYear(), Math.abs(lunar.getMonth()), lunar.getDay(), lunar.getMonth() < 0);
        assertEquals(solar, back);
        assertEquals(LunarSolarUtil.solarToLunar(solar), LunarSolarUtil.solarToLunar(back));
    }
}
