package com.company.grc.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service to manage the rolling 18-month retention policy for vendor delayed months
 * across GSTR-1, GSTR-3B, and GSTR-7.
 * 
 * Rules:
 * 1. Only store and retain delayed months within the last 18 months from current date.
 * 2. Any month older than 18 months automatically expires / prunes.
 * 3. Previously updated monthly records within the 18-month window must not be
 *    overwritten or lost when new sheets or updates are imported.
 */
@Service
public class ComplianceRetentionService {

    public static final int RETENTION_WINDOW_MONTHS = 18;

    private static final String[] MONTH_NAMES = {
            "", "Jan", "Feb", "March", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private static final String[] MONTH_PREFIXES = {
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec"
    };

    /**
     * Calculates the earliest allowed YearMonth for the rolling 18-month window.
     */
    public static YearMonth getEarliestAllowedMonth() {
        return YearMonth.now().minusMonths(RETENTION_WINDOW_MONTHS - 1);
    }

    /**
     * Checks if a given YearMonth is within the rolling 18-month window.
     */
    public static boolean isWithin18MonthWindow(YearMonth ym) {
        if (ym == null) return false;
        YearMonth now = YearMonth.now();
        YearMonth earliest = getEarliestAllowedMonth();
        return !ym.isBefore(earliest) && !ym.isAfter(now);
    }

    /**
     * Prunes a comma-separated delayed months string to only include months within
     * the active rolling 18-month retention window.
     */
    public static String pruneTo18MonthWindow(String monthsStr) {
        if (monthsStr == null || monthsStr.trim().isBlank()) {
            return null;
        }

        YearMonth currentYm = YearMonth.now();
        YearMonth earliestYm = getEarliestAllowedMonth();
        int currentYear = currentYm.getYear();

        Set<YearMonth> validMonths = parseYearMonths(monthsStr, currentYm, earliestYm);

        if (validMonths.isEmpty()) {
            return null;
        }

        // Sort chronologically
        List<YearMonth> sorted = new ArrayList<>(validMonths);
        Collections.sort(sorted);

        return sorted.stream()
                .map(ym -> formatYearMonth(ym, currentYear))
                .collect(Collectors.joining(", "));
    }

    /**
     * Merges newly incoming delayed months with existing historical delayed months,
     * ensuring all records within the last 18 months are retained and not overwritten.
     */
    public static String mergeAndRetainMonths(String existingMonthsStr, String newMonthsStr) {
        YearMonth currentYm = YearMonth.now();
        YearMonth earliestYm = getEarliestAllowedMonth();
        int currentYear = currentYm.getYear();

        Set<YearMonth> combined = new TreeSet<>();
        combined.addAll(parseYearMonths(existingMonthsStr, currentYm, earliestYm));
        combined.addAll(parseYearMonths(newMonthsStr, currentYm, earliestYm));

        if (combined.isEmpty()) {
            return null;
        }

        return combined.stream()
                .map(ym -> formatYearMonth(ym, currentYear))
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns the number of delayed months falling strictly within the 18-month window.
     */
    public static int countMonthsIn18MonthWindow(String monthsStr) {
        if (monthsStr == null || monthsStr.trim().isBlank()) {
            return 0;
        }
        YearMonth currentYm = YearMonth.now();
        YearMonth earliestYm = getEarliestAllowedMonth();
        return parseYearMonths(monthsStr, currentYm, earliestYm).size();
    }

    /**
     * Helper to parse comma-separated month strings into valid YearMonths within the window.
     */
    public static Set<YearMonth> parseYearMonths(String monthsStr, YearMonth currentYm, YearMonth earliestYm) {
        if (monthsStr == null || monthsStr.trim().isBlank()) {
            return Collections.emptySet();
        }

        Set<YearMonth> result = new HashSet<>();
        String[] tokens = monthsStr.split("[,;]+");

        for (String raw : tokens) {
            YearMonth ym = parseMonthToken(raw, currentYm, earliestYm);
            if (ym != null && !ym.isBefore(earliestYm) && !ym.isAfter(currentYm)) {
                result.add(ym);
            }
        }

        return result;
    }

    /**
     * Parses an individual month token like "Sep 2025", "Jan", "Oct 25", or "March".
     */
    public static YearMonth parseMonthToken(String token, YearMonth currentYm, YearMonth earliestYm) {
        if (token == null || token.trim().isBlank()) {
            return null;
        }

        String clean = token.trim().toLowerCase();

        // Check if there is a 4-digit year (e.g., 2024, 2025, 2026)
        Integer year = null;
        Matcher m4 = Pattern.compile("\\b(20\\d{2})\\b").matcher(clean);
        if (m4.find()) {
            year = Integer.parseInt(m4.group(1));
            clean = clean.replace(m4.group(1), "").trim();
        } else {
            // Check for 2-digit year (e.g., '25 or -25 or /25)
            Matcher m2 = Pattern.compile("[-/\\s'](\\d{2})\\b").matcher(clean);
            if (m2.find()) {
                year = 2000 + Integer.parseInt(m2.group(1));
                clean = clean.replace(m2.group(0), "").trim();
            }
        }

        // Match month prefix
        Integer month = null;
        for (int i = 0; i < MONTH_PREFIXES.length; i++) {
            if (clean.contains(MONTH_PREFIXES[i])) {
                month = i + 1;
                break;
            }
        }

        if (month == null) {
            return null;
        }

        // If year was explicitly provided:
        if (year != null) {
            try {
                return YearMonth.of(year, month);
            } catch (Exception e) {
                return null;
            }
        }

        // If year was not specified, check if current year month fits within the window
        YearMonth ymCurrent = YearMonth.of(currentYm.getYear(), month);
        if (!ymCurrent.isBefore(earliestYm) && !ymCurrent.isAfter(currentYm)) {
            return ymCurrent;
        }

        // Otherwise check previous year
        YearMonth ymPrev = YearMonth.of(currentYm.getYear() - 1, month);
        if (!ymPrev.isBefore(earliestYm) && !ymPrev.isAfter(currentYm)) {
            return ymPrev;
        }

        return ymCurrent;
    }

    /**
     * Formats a YearMonth into the standard convention:
     * - Previous years include the 4-digit year suffix (e.g. "Sep 2025")
     * - Current year displays month name only (e.g. "Jan", "Nov")
     */
    public static String formatYearMonth(YearMonth ym, int currentYear) {
        String mName = MONTH_NAMES[ym.getMonthValue()];
        if (ym.getYear() < currentYear) {
            return mName + " " + ym.getYear();
        } else {
            return mName;
        }
    }
}
