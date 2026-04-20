package com.literiskapp.processing;

import com.literiskapp.api.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Builds the OLAP {@link Result} fact rows from cashflows and deal state.
 *
 * <p>Grouping key: (assetLiability, intervalDate, dealId, dealCurrency).
 *
 * <p>Measures are computed as follows:
 * <ul>
 *   <li><b>Flow measures</b> (principalFlow, interestIncome/Expense, couponIncome, fxPnl, npv)
 *       – summed over cashflows falling in the interval, converted to reporting currency.</li>
 *   <li><b>Snapshot measures</b> (bookValue, nominalBalance) – the state of the deal
 *       at the interval-start date, taken from the latest cashflow on or before that date,
 *       or the deal's opening state if none. Also converted to reporting currency.</li>
 * </ul>
 */
@Component
public class ResultAggregator {

    public List<Result> aggregate(List<Deal> deals,
                                  List<Cashflow> cashflows,
                                  MarketDataService md,
                                  ProcessingSettings s) {

        Map<String, Deal> dealById = new HashMap<>();
        for (Deal d : deals) dealById.put(d.id, d);

        // Group cashflows by deal id for per-deal iteration.
        Map<String, List<Cashflow>> byDeal = new HashMap<>();
        for (Cashflow cf : cashflows) {
            byDeal.computeIfAbsent(cf.deal, k -> new ArrayList<>()).add(cf);
        }
        for (List<Cashflow> list : byDeal.values()) list.sort(Comparator.comparing(c -> c.date));

        String reportCcy = s.reportingCurrency == null ? "USD" : s.reportingCurrency;
        Timeband tb = s.timeband == null ? Timeband.Daily : s.timeband;

        List<LocalDate> allIntervals = IntervalCalculator.intervalStartsBetween(
                s.processingStartDate, s.processingEndDate, tb);

        Map<ResultKey, Result> acc = new LinkedHashMap<>();

        // ----- Flow aggregation: walk cashflows -----
        for (Cashflow cf : cashflows) {
            Deal deal = dealById.get(cf.deal);
            if (deal == null) continue;
            LocalDate intervalStart = IntervalCalculator.startOfInterval(cf.date, tb);
            ResultKey key = new ResultKey(deal.assetLiability, intervalStart, deal.id, deal.currency);
            Result r = acc.computeIfAbsent(key, this::blank);

            // Convert cashflow currency -> reporting currency at cashflow date
            double fx = md.fxRate(cf.currency, reportCcy, cf.date);
            double amountRep = nz(cf.amount) * fx;
            double npvRep = nz(cf.npv) * fx;

            switch (cf.type == null ? "" : cf.type.toUpperCase()) {
                case "INTEREST" -> {
                    if ("ASSET".equalsIgnoreCase(deal.assetLiability)) r.interestIncome += amountRep;
                    else r.interestExpense += amountRep;
                }
                case "COUPON" -> r.couponIncome += amountRep;
                case "PRINCIPAL", "MATURITY", "FX_NEAR", "FX_FAR" -> r.principalFlow += amountRep;
                case "FX_PNL" -> r.fxPnl += amountRep;
                default -> {}
            }
            r.npv += npvRep;
        }

        // ----- Snapshot measures (book_value, nominal_balance) -----
        // For each deal, at each interval start inside the window, compute the
        // running balance from cashflows. If no cashflow has occurred yet,
        // fall back to the deal's opening state.
        for (Deal deal : deals) {
            List<Cashflow> dealCfs = byDeal.getOrDefault(deal.id, List.of());
            for (LocalDate ivl : allIntervals) {
                double bvDeal = snapshotBookValue(deal, dealCfs, ivl);
                double nomDeal = snapshotNominal(deal, dealCfs, ivl);
                double fx = md.fxRate(deal.currency, reportCcy, ivl);
                ResultKey key = new ResultKey(deal.assetLiability, ivl, deal.id, deal.currency);
                Result r = acc.computeIfAbsent(key, this::blank);
                r.bookValue = bvDeal * fx;
                r.nominalBalance = nomDeal * fx;
            }
        }

        return new ArrayList<>(acc.values());
    }

    // ---- snapshot helpers ----

    private double snapshotBookValue(Deal d, List<Cashflow> ordered, LocalDate at) {
        // latest bookValue on any cashflow on or before {@code at}
        Cashflow last = null;
        for (Cashflow cf : ordered) {
            if (!cf.date.isAfter(at)) last = cf; else break;
        }
        if (last != null && last.bookValue != null) return last.bookValue;
        // Before first cashflow: opening state
        return d.bookValue != null ? d.bookValue :
               (d.originalPrincipal != null ? d.originalPrincipal :
               (d.faceValue != null ? d.faceValue : 0.0));
    }

    private double snapshotNominal(Deal d, List<Cashflow> ordered, LocalDate at) {
        Cashflow last = null;
        for (Cashflow cf : ordered) {
            if (!cf.date.isAfter(at)) last = cf; else break;
        }
        if (last != null && last.remainingPrincipal != null) return last.remainingPrincipal;
        return d.originalPrincipal != null ? d.originalPrincipal :
               (d.faceValue != null ? d.faceValue : 0.0);
    }

    private Result blank(ResultKey k) {
        Result r = new Result();
        r.assetLiability = k.assetLiability();
        r.interval = k.interval();
        r.deal = k.deal();
        r.currency = k.currency();
        r.bookValue = 0.0;
        r.principalFlow = 0.0;
        r.interestIncome = 0.0;
        r.interestExpense = 0.0;
        r.couponIncome = 0.0;
        r.fxPnl = 0.0;
        r.npv = 0.0;
        r.nominalBalance = 0.0;
        return r;
    }

    private static double nz(Double d) { return d == null ? 0.0 : d; }

    private record ResultKey(String assetLiability, LocalDate interval, String deal, String currency) {}
}
