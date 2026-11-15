package com.mouhin.family.tree.common.util;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.time.LocalDate;

/**
 * 农历（阴历）↔阳历（公历）换算工具。
 * <p>
 * 基于 6tail lunar（与前端 lunar-javascript 同作者、同版本换算表），保证前端实时预览
 * 与后端落库结果完全一致。出生日期以阳历为唯一真值，农历一律由阳历推导，故 AI 导入、
 * GEDCOM 导入及任何仅提交阳历的入口都能自动补齐农历；篡改农历字符串会被后端以阳历重算纠正。
 *
 * @author Family-Tree
 * @date 2026-11-14
 */
public final class LunarSolarUtil {

    private LunarSolarUtil() {
    }

    /**
     * 阳历日期转农历字符串（含干支纪年）。
     *
     * @param solarDate 阳历日期，可为 null
     * @return 形如"丙寅年四月廿一"的农历字符串；solarDate 为 null 时返回 null
     */
    public static String solarToLunar(LocalDate solarDate) {
        if (solarDate == null) {
            return null;
        }
        return format(solarDate.getYear(), solarDate.getMonthValue(), solarDate.getDayOfMonth());
    }

    /**
     * 阳历日期字符串（yyyy-MM-dd）转农历字符串。
     *
     * @param solarDateText 阳历日期字符串，可为 null 或空白
     * @return 农历字符串；入参为空时返回 null
     */
    public static String solarTextToLunar(String solarDateText) {
        if (solarDateText == null || solarDateText.isBlank()) {
            return null;
        }
        return solarToLunar(LocalDate.parse(solarDateText.trim()));
    }

    /**
     * 宽松版阳历字符串转农历：空白或非法格式一律返回 null，不抛异常。
     * <p>
     * 用于 sys_user.birth_date 这类历史上未做格式校验的自由字符串，避免个人信息保存被非法日期打断。
     *
     * @param solarDateText 阳历日期字符串
     * @return 农历字符串；空白或无法解析时返回 null
     */
    public static String solarTextToLunarOrNull(String solarDateText) {
        if (solarDateText == null || solarDateText.isBlank()) {
            return null;
        }
        try {
            return solarToLunar(LocalDate.parse(solarDateText.trim()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 阳历日期转其所属的农历年份数字。
     * <p>
     * 农历以正月为岁首，阳历 1 月至春节前的日期仍属上一个农历年，故结果可能与阳历年相差 1。
     *
     * @param solarDate 阳历日期，可为 null
     * @return 农历年份；solarDate 为 null 时返回 null
     */
    public static Integer solarToLunarYear(LocalDate solarDate) {
        if (solarDate == null) {
            return null;
        }
        Lunar lunar = Solar.fromYmd(solarDate.getYear(), solarDate.getMonthValue(), solarDate.getDayOfMonth())
                .getLunar();
        return lunar.getYear();
    }

    /**
     * 农历日期转阳历日期。
     *
     * @param lunarYear  农历年
     * @param lunarMonth 农历月（1–12）
     * @param lunarDay   农历日（1–30）
     * @param isLeap     是否闰月
     * @return 对应阳历日期
     */
    public static LocalDate lunarToSolar(int lunarYear, int lunarMonth, int lunarDay, boolean isLeap) {
        int month = isLeap ? -lunarMonth : lunarMonth;
        Solar solar = Lunar.fromYmd(lunarYear, month, lunarDay).getSolar();
        return LocalDate.of(solar.getYear(), solar.getMonth(), solar.getDay());
    }

    /**
     * 由阳历三元组构造与前端一致的农历展示串：干支年 + 中文月 + 中文日（闰月自动带"闰"前缀）。
     */
    private static String format(int year, int month, int day) {
        Lunar lunar = Solar.fromYmd(year, month, day).getLunar();
        return lunar.getYearInGanZhi() + "年" + lunar.getMonthInChinese() + "月" + lunar.getDayInChinese();
    }
}
