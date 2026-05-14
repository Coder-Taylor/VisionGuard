package com.example.myapplication.util

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * 农历↔公历互相转换工具。
 * 基于 1900-2100 年农历数据查表法。
 * 基准：1900-01-31 = 农历 1900 年正月初一
 */
object LunarCalendar {

    data class LunarDate(val year: Int, val month: Int, val day: Int, val isLeapMonth: Boolean = false)

    // 农历年数据（1900-2100）：bit 0-3=闰月月份, bit 4-15=12个月大小(1=30天,0=29天), bit 16-19=闰月大小
    private val lunarInfo = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06aa0, 0x1a6c4, 0x0aae0,
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a4d0, 0x0d150, 0x0f252,
        0x0d520
    )

    // 基准：1900-01-31 = 农历 1900 年正月初一
    private val baseCalendar: GregorianCalendar by lazy {
        GregorianCalendar(1900, 0, 31).also { it.isLenient = true }
    }

    /** 返回该农历年的闰月月份(1-12)，无闰月返回 0 */
    fun leapMonthOf(year: Int): Int = lunarInfo[year - 1900] and 0xf

    /** 农历→公历 */
    fun lunarToSolar(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean = false): Triple<Int, Int, Int>? {
        if (lunarYear < 1900 || lunarYear > 2100 || lunarMonth < 1 || lunarMonth > 12 || lunarDay < 1) return null

        // 1. 计算从 1900 年正月初一到目标年正月初一的天数
        var totalDays = 0L
        for (y in 1900 until lunarYear) {
            totalDays += lunarYearDays(y)
        }

        // 2. 加上目标年内到指定月份的天数
        val leapMonth = leapMonthOf(lunarYear)
        var passedLeap = false
        for (m in 1 until lunarMonth) {
            totalDays += monthDays(lunarYear, m)
            if (m == leapMonth && !passedLeap && !isLeapMonth) {
                passedLeap = true
                totalDays += leapMonthDays(lunarYear)
            }
        }
        // 如果目标月份是闰月，先加上前一个同名月
        if (isLeapMonth && lunarMonth == leapMonth) {
            totalDays += monthDays(lunarYear, lunarMonth)
        }

        // 3. 加上日期（初一 = 第1天，所以加 lunarDay-1）
        totalDays += (lunarDay - 1).toLong()

        // 4. 基准日 + 偏移天数 = 公历日期
        val result = baseCalendar.clone() as GregorianCalendar
        result.add(Calendar.DAY_OF_YEAR, totalDays.toInt())
        return Triple(result.get(Calendar.YEAR), result.get(Calendar.MONTH) + 1, result.get(Calendar.DAY_OF_MONTH))
    }

    /** 公历→农历 */
    fun solarToLunar(solarYear: Int, solarMonth: Int, solarDay: Int): LunarDate? {
        if (solarYear < 1900 || solarYear > 2100) return null

        val targetDate = GregorianCalendar(solarYear, solarMonth - 1, solarDay)
        // 计算目标日期距离 1900-01-31 的天数
        val diffDays = ((targetDate.timeInMillis - baseCalendar.timeInMillis) / 86400000L).toInt()
        if (diffDays < 0) return null

        // 逐年减去农历年天数
        var remaining = diffDays
        var lunarYear = 1900
        while (lunarYear <= 2100) {
            val yearDays = lunarYearDays(lunarYear)
            if (remaining < yearDays) break
            remaining -= yearDays
            lunarYear++
        }

        // 在农历年内逐月定位
        val leapMonth = leapMonthOf(lunarYear)
        var lunarMonth = 1
        var isLeap = false

        for (m in 1..12) {
            val daysInMonth = monthDays(lunarYear, m)
            if (remaining < daysInMonth) {
                lunarMonth = m
                return LunarDate(lunarYear, lunarMonth, remaining + 1, false)
            }
            remaining -= daysInMonth

            // 检查闰月
            if (m == leapMonth) {
                val leapDays = leapMonthDays(lunarYear)
                if (remaining < leapDays) {
                    lunarMonth = m
                    return LunarDate(lunarYear, lunarMonth, remaining + 1, true)
                }
                remaining -= leapDays
            }
        }

        return null
    }

    // ---- helpers ----

    private fun lunarYearDays(year: Int): Int {
        var total = 0
        for (i in 0..11) total += monthDays(year, i + 1)
        val leap = lunarInfo[year - 1900] and 0xf
        if (leap != 0) total += leapMonthDays(year)
        return total
    }

    private fun monthDays(year: Int, month: Int): Int {
        // bit 4-15: month 1 uses bit 15, month 2 uses bit 14, ..., month 12 uses bit 4
        return if ((lunarInfo[year - 1900] and (0x10000 shr month)) != 0) 30 else 29
    }

    private fun leapMonthDays(year: Int): Int {
        // bit 16: 1=30 days, 0=29 days
        return if ((lunarInfo[year - 1900] and 0x10000) != 0) 30 else 29
    }
}
