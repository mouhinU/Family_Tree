package com.mouhin.family.tree.common.util;

import java.time.LocalDate;

/**
 * 农历日期工具类。
 * <p>
 * 提供公历↔农历转换、天干地支纪年、农历日期格式化等功能。
 * 数据范围：1900年–2100年。
 * <p>
 * 算法基于查表法，LUNAR_INFO 数组存储每年农历历法信息。
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
public final class LunarDateUtils {

    /**
     * 天干
     */
    private static final String[] HEAVENLY_STEMS = {
            "甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"
    };
    /**
     * 地支
     */
    private static final String[] EARTHLY_BRANCHES = {
            "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
    };
    /**
     * 生肖
     */
    private static final String[] ZODIAC_ANIMALS = {
            "鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"
    };
    /**
     * 农历月份名称
     */
    private static final String[] LUNAR_MONTH_NAMES = {
            "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"
    };
    /**
     * 农历日期名称（初一到三十）
     */
    private static final String[] LUNAR_DAY_NAMES = {
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };
    /**
     * 农历历法数据表（1900–2100，共201年）。
     * 每个 int 值编码该年的农历信息：
     * <ul>
     *   <li>Bit 4–15：11月至次年10月各月天数（0=29天，1=30天）</li>
     *   <li>Bit 16：闰月天数（0=29天，1=30天）</li>
     *   <li>Bit 17–20：闰月月份（0=无闰月）</li>
     * </ul>
     */
    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
            0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a4d0, 0x0d150, 0x0f252, // 2090-2099
            0x0d520                                                                                     // 2100
    };
    /**
     * 基准年
     */
    private static final int BASE_YEAR = 1900;
    /**
     * 1900年农历正月初一对应的公历日期：1900-01-31
     */
    private static final LocalDate LUNAR_EPOCH = LocalDate.of(1900, 1, 31);

    private LunarDateUtils() {
    }

    // ========== 天干地支 ==========

    /**
     * 计算年份的天干地支。
     *
     * @param year 公历年份
     * @return 天干地支字符串，如"甲子"
     */
    public static String getGanZhi(int year) {
        int stemIndex = (year - 4) % 10;
        int branchIndex = (year - 4) % 12;
        return HEAVENLY_STEMS[stemIndex] + EARTHLY_BRANCHES[branchIndex];
    }

    /**
     * 计算年份的生肖。
     *
     * @param year 公历年份
     * @return 生肖字符串，如"鼠"
     */
    public static String getZodiac(int year) {
        int index = (year - 4) % 12;
        return ZODIAC_ANIMALS[index];
    }

    // ========== 农历基础计算 ==========

    /**
     * 获取指定农历年的闰月月份（0=无闰月）。
     */
    public static int getLeapMonth(int lunarYear) {
        return LUNAR_INFO[lunarYear - BASE_YEAR] & 0xf;
    }

    /**
     * 获取指定农历年闰月的天数（0=无闰月）。
     */
    public static int getLeapMonthDays(int lunarYear) {
        if (getLeapMonth(lunarYear) == 0) {
            return 0;
        }
        return (LUNAR_INFO[lunarYear - BASE_YEAR] & 0x10000) != 0 ? 30 : 29;
    }

    /**
     * 获取指定农历年普通月的天数。
     *
     * @param lunarYear 农历年
     * @param month     月份（1–12）
     * @return 该月天数（29 或 30）
     */
    public static int getMonthDays(int lunarYear, int month) {
        // bit 4 对应月11, bit 5 对应月12, bit 6 对应月1, ..., bit 15 对应月10
        int bitOffset;
        if (month <= 10) {
            bitOffset = 16 - month;
        } else {
            bitOffset = 16 - month + 12;
        }
        // 等价：month 11→bit4, 12→bit5, 1→bit6, 2→bit7, ..., 10→bit15
        // 简化：bit = (month + 5) % 12 + 4
        int bit = ((month + 5) % 12) + 4;
        return (LUNAR_INFO[lunarYear - BASE_YEAR] & (1 << bit)) != 0 ? 30 : 29;
    }

    /**
     * 获取指定农历年的总天数。
     */
    public static int getYearDays(int lunarYear) {
        int sum = 348; // 12个月 × 29天 = 348
        int info = LUNAR_INFO[lunarYear - BASE_YEAR];
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            if ((info & i) != 0) {
                sum += 1;
            }
        }
        return sum + getLeapMonthDays(lunarYear);
    }

    // ========== 公历→农历 ==========

    /**
     * 公历日期转农历日期。
     *
     * @param solarDate 公历日期
     * @return 农历日期数组 [年, 月, 日]，月份为负数表示闰月
     */
    public static int[] solarToLunar(LocalDate solarDate) {
        int offset = (int) (solarDate.toEpochDay() - LUNAR_EPOCH.toEpochDay());

        int lunarYear = BASE_YEAR;
        int yearDays;
        for (; lunarYear <= 2100; lunarYear++) {
            yearDays = getYearDays(lunarYear);
            if (offset < yearDays) {
                break;
            }
            offset -= yearDays;
        }
        if (lunarYear > 2100) {
            lunarYear = 2100;
        }

        // 计算月日
        int leapMonth = getLeapMonth(lunarYear);
        int lunarMonth = 1;
        boolean isLeap = false;

        for (int i = 1; i <= 12; i++) {
            int monthDays = getMonthDays(lunarYear, i);
            if (offset < monthDays) {
                lunarMonth = i;
                break;
            }
            offset -= monthDays;

            // 如果有闰月，且当前月就是闰月前的那个月
            if (i == leapMonth) {
                int leapDays = getLeapMonthDays(lunarYear);
                if (offset < leapDays) {
                    lunarMonth = i;
                    isLeap = true;
                    break;
                }
                offset -= leapDays;
            }
        }

        int lunarDay = offset + 1;
        if (isLeap) {
            lunarMonth = -lunarMonth; // 负数表示闰月
        }

        return new int[]{lunarYear, lunarMonth, lunarDay};
    }

    // ========== 农历→公历 ==========

    /**
     * 农历日期转公历日期。
     *
     * @param lunarYear  农历年
     * @param lunarMonth 农历月（负数表示闰月）
     * @param lunarDay   农历日
     * @return 公历日期
     */
    public static LocalDate lunarToSolar(int lunarYear, int lunarMonth, int lunarDay) {
        int offset = 0;

        // 累加整年天数
        for (int y = BASE_YEAR; y < lunarYear; y++) {
            offset += getYearDays(y);
        }

        // 累加整月天数
        boolean isLeapMonth = lunarMonth < 0;
        int absMonth = Math.abs(lunarMonth);
        int leapMonth = getLeapMonth(lunarYear);

        for (int m = 1; m < absMonth; m++) {
            offset += getMonthDays(lunarYear, m);
            if (m == leapMonth) {
                offset += getLeapMonthDays(lunarYear);
            }
        }

        // 加上日数
        offset += lunarDay - 1;

        return LUNAR_EPOCH.plusDays(offset);
    }

    // ========== 格式化 ==========

    /**
     * 格式化农历月份名称。
     *
     * @param month  农历月（正数）
     * @param isLeap 是否闰月
     * @return 月份名称，如"正月""闰四月"
     */
    public static String formatLunarMonth(int month, boolean isLeap) {
        String name = LUNAR_MONTH_NAMES[Math.abs(month) - 1] + "月";
        return isLeap ? "闰" + name : name;
    }

    /**
     * 格式化农历日期名称。
     *
     * @param day 农历日（1–30）
     * @return 日期名称，如"初一""十五"
     */
    public static String formatLunarDay(int day) {
        if (day < 1 || day > 30) {
            return "";
        }
        return LUNAR_DAY_NAMES[day - 1];
    }

    /**
     * 将公历日期格式化为农历字符串（含天干地支）。
     *
     * @param solarDate 公历日期
     * @return 格式化字符串，如"甲子年正月初五"
     */
    public static String formatLunarFull(LocalDate solarDate) {
        int[] lunar = solarToLunar(solarDate);
        int year = lunar[0];
        int month = lunar[1];
        int day = lunar[2];
        boolean isLeap = month < 0;

        String ganZhi = getGanZhi(year);
        String monthStr = formatLunarMonth(Math.abs(month), isLeap);
        String dayStr = formatLunarDay(day);

        return ganZhi + "年" + monthStr + dayStr;
    }

    /**
     * 解析农历日期字符串。
     * 支持格式："1950-正月初五"、"1950-1-5" 等。
     *
     * @param lunarStr 农历日期字符串
     * @return 农历日期数组 [年, 月, 日]，解析失败返回 null
     */
    public static int[] parseLunarString(String lunarStr) {
        if (lunarStr == null || lunarStr.isBlank()) {
            return null;
        }
        try {
            String[] parts = lunarStr.trim().split("-");
            if (parts.length != 3) {
                return null;
            }
            int year = Integer.parseInt(parts[0].trim());
            int month;
            int day;

            // 尝试数字格式
            try {
                month = Integer.parseInt(parts[1].trim());
                day = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                // 尝试中文格式
                month = parseChineseMonth(parts[1].trim());
                day = parseChineseDay(parts[2].trim());
            }

            if (month == 0 || day == 0) {
                return null;
            }
            return new int[]{year, month, day};
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseChineseMonth(String str) {
        boolean isLeap = str.startsWith("闰");
        String core = isLeap ? str.substring(1) : str;
        if (core.endsWith("月")) {
            core = core.substring(0, core.length() - 1);
        }
        for (int i = 0; i < LUNAR_MONTH_NAMES.length; i++) {
            if (LUNAR_MONTH_NAMES[i].equals(core)) {
                return isLeap ? -(i + 1) : (i + 1);
            }
        }
        return 0;
    }

    private static int parseChineseDay(String str) {
        for (int i = 0; i < LUNAR_DAY_NAMES.length; i++) {
            if (LUNAR_DAY_NAMES[i].equals(str)) {
                return i + 1;
            }
        }
        return 0;
    }
}
