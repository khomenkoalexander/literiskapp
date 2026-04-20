package com.literiskapp.processing;

import com.literiskapp.api.Timeband;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Calendar-based interval boundaries. All intervals are aligned to real
 * calendar periods (ISO week Mon-Sun, first-of-month, first-of-quarter, Jan 1)
 * and identified by the START of the period.
 */
public final class IntervalCalculator {

    private IntervalCalculator() {}

    /** Return the start-of-period date that contains {@code date} for the given timeband. */
    public static LocalDate startOfInterval(LocalDate date, Timeband tb) {
        return switch (tb) {
            case Daily -> date;
            case Weekly -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case Monthly -> date.withDayOfMonth(1);
            case Quarterly -> {
                int month = ((date.getMonthValue() - 1) / 3) * 3 + 1;
                yield LocalDate.of(date.getYear(), month, 1);
            }
            case Yearly -> LocalDate.of(date.getYear(), 1, 1);
        };
    }

    /** Move to the NEXT interval start after the one containing {@code date}. */
    public static LocalDate nextInterval(LocalDate date, Timeband tb) {
        LocalDate start = startOfInterval(date, tb);
        return switch (tb) {
            case Daily -> start.plusDays(1);
            case Weekly -> start.plusWeeks(1);
            case Monthly -> start.plusMonths(1);
            case Quarterly -> start.plusMonths(3);
            case Yearly -> start.plusYears(1);
        };
    }

    /**
     * All interval-start dates that overlap the inclusive range [from, to].
     * Returns the earliest interval start that contains {@code from} up to the one that
     * contains {@code to}.
     */
    public static List<LocalDate> intervalStartsBetween(LocalDate from, LocalDate to, Timeband tb) {
        List<LocalDate> out = new ArrayList<>();
        if (from == null || to == null || from.isAfter(to)) return out;
        LocalDate cur = startOfInterval(from, tb);
        LocalDate last = startOfInterval(to, tb);
        while (!cur.isAfter(last)) {
            out.add(cur);
            cur = nextInterval(cur, tb);
        }
        return out;
    }
}
