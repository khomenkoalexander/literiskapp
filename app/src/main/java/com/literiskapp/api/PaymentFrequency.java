package com.literiskapp.api;

import java.time.Period;

/**
 * Payment / accrual frequency used for interest, principal and coupon schedules.
 * Stored as TEXT in the database.
 *
 * <p>{@code SEMI_ANNUAL} and {@code YEARLY} are accepted aliases retained for
 * backward-compatibility with older data; canonical values are {@code SEMIANNUAL}
 * and {@code ANNUAL}.
 */
public enum PaymentFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    SEMIANNUAL,
    /** Alias for {@link #SEMIANNUAL}. */
    SEMI_ANNUAL,
    ANNUAL,
    /** Alias for {@link #ANNUAL}. */
    YEARLY;

    /** Convert to a {@link Period} suitable for date arithmetic. */
    public Period toPeriod() {
        return switch (this) {
            case DAILY       -> Period.ofDays(1);
            case WEEKLY      -> Period.ofWeeks(1);
            case MONTHLY     -> Period.ofMonths(1);
            case QUARTERLY   -> Period.ofMonths(3);
            case SEMIANNUAL,
                 SEMI_ANNUAL -> Period.ofMonths(6);
            case ANNUAL,
                 YEARLY      -> Period.ofYears(1);
        };
    }
}
