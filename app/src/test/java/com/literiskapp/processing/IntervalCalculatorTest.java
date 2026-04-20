package com.literiskapp.processing;

import com.literiskapp.api.Timeband;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalCalculatorTest {

    @Test
    void daily_interval_is_the_date_itself() {
        LocalDate d = LocalDate.of(2024, 6, 15);
        assertThat(IntervalCalculator.startOfInterval(d, Timeband.Daily)).isEqualTo(d);
    }

    @Test
    void weekly_snaps_back_to_monday_mid_week() {
        // 2024-06-15 is a Saturday
        assertThat(IntervalCalculator.startOfInterval(LocalDate.of(2024, 6, 15), Timeband.Weekly))
                .isEqualTo(LocalDate.of(2024, 6, 10)); // Monday
    }

    @Test
    void weekly_on_monday_is_same_day() {
        assertThat(IntervalCalculator.startOfInterval(LocalDate.of(2024, 6, 10), Timeband.Weekly))
                .isEqualTo(LocalDate.of(2024, 6, 10));
    }

    @Test
    void monthly_snaps_to_first_of_month_even_on_29_day_month() {
        assertThat(IntervalCalculator.startOfInterval(LocalDate.of(2024, 2, 29), Timeband.Monthly))
                .isEqualTo(LocalDate.of(2024, 2, 1));
    }

    @Test
    void quarterly_buckets_are_jan_apr_jul_oct() {
        assertThat(IntervalCalculator.startOfInterval(LocalDate.of(2024, 1, 31), Timeband.Quarterly))
                .isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(IntervalCalculator.startOfInterval(LocalDate.of(2024, 5, 15), Timeband.Quarterly))
                .isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(IntervalCalculator.startOfInterval(LocalDate.of(2024, 9, 30), Timeband.Quarterly))
                .isEqualTo(LocalDate.of(2024, 7, 1));
        assertThat(IntervalCalculator.startOfInterval(LocalDate.of(2024, 12, 31), Timeband.Quarterly))
                .isEqualTo(LocalDate.of(2024, 10, 1));
    }

    @Test
    void yearly_snaps_to_jan_1() {
        assertThat(IntervalCalculator.startOfInterval(LocalDate.of(2024, 7, 15), Timeband.Yearly))
                .isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void monthly_intervals_between_cover_calendar_months_not_processing_start() {
        // processing begins mid-month — the first interval is still the 1st of that month
        List<LocalDate> ivls = IntervalCalculator.intervalStartsBetween(
                LocalDate.of(2024, 3, 15), LocalDate.of(2024, 6, 10), Timeband.Monthly);
        assertThat(ivls).containsExactly(
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 4, 1),
                LocalDate.of(2024, 5, 1),
                LocalDate.of(2024, 6, 1));
    }

    @Test
    void weekly_intervals_cross_month_boundaries() {
        // Mon 2024-04-29 through Sun 2024-05-12 covers 2 ISO weeks
        List<LocalDate> ivls = IntervalCalculator.intervalStartsBetween(
                LocalDate.of(2024, 4, 29), LocalDate.of(2024, 5, 12), Timeband.Weekly);
        assertThat(ivls).containsExactly(
                LocalDate.of(2024, 4, 29),
                LocalDate.of(2024, 5, 6));
    }
}
