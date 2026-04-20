package com.literiskapp.processing;

import com.literiskapp.api.Cashflow;
import com.literiskapp.api.Deal;
import com.literiskapp.api.DealType;
import com.literiskapp.api.PaymentFrequency;
import com.literiskapp.api.ProcessingSettings;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Produces the list of cashflows for one deal over the processing window.
 * Each implementation knows one deal {@link DealType} and is stateless.
 */
public interface CashflowGenerator {

    /** Deal type this generator handles. */
    DealType supports();

    List<Cashflow> generate(Deal deal, MarketDataService md, ProcessingSettings settings);

    // ---------- Shared helpers ----------

    /** Null-safe frequency-to-period converter; defaults to MONTHLY when {@code freq} is null. */
    default Period parseFreq(PaymentFrequency freq) {
        return freq == null ? Period.ofMonths(1) : freq.toPeriod();
    }

    /** ACT/360 year fraction. */
    default double yearFraction(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) / 360.0;
    }

    /** Simple-compounded discount factor from {@code asOf} to {@code payDate} using {@code zeroRate}. */
    default double discountFactor(double zeroRate, LocalDate asOf, LocalDate payDate) {
        double t = yearFraction(asOf, payDate);
        if (t <= 0) return 1.0;
        return 1.0 / (1.0 + zeroRate * t);
    }

    /** Clamp a schedule point to the processing window. */
    default boolean inWindow(LocalDate date, ProcessingSettings s) {
        if (date == null) return false;
        if (s.processingStartDate != null && date.isBefore(s.processingStartDate)) return false;
        if (s.processingEndDate != null && date.isAfter(s.processingEndDate)) return false;
        return true;
    }
}
