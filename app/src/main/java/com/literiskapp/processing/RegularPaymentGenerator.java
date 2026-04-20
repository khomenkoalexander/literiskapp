package com.literiskapp.processing;

import com.literiskapp.api.Cashflow;
import com.literiskapp.api.Deal;
import com.literiskapp.api.ProcessingSettings;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Amortising loan / regular payment schedule. LINEAR amortisation means equal
 * principal installments; interest is accrued on the running outstanding
 * principal at ACT/360.
 */
@Component
public class RegularPaymentGenerator implements CashflowGenerator {

    @Override public String supports() { return "REGULAR_PAYMENT"; }

    @Override
    public List<Cashflow> generate(Deal deal, MarketDataService md, ProcessingSettings s) {
        List<Cashflow> out = new ArrayList<>();
        if (deal.originalPrincipal == null || deal.maturityDate == null) return out;

        double principal = deal.originalPrincipal;
        double rate = deal.nominalRate == null ? 0.0 : deal.nominalRate;

        // Build the full principal schedule up to maturity so we know the outstanding
        // balance at any interest-payment date even when payments happen before the
        // processing window opens.
        LocalDate prinStart = deal.prinPayStart != null ? deal.prinPayStart : deal.dealDate;
        Period prinStep = parseFreq(deal.prinPayFreq);
        List<LocalDate> prinDates = schedule(prinStart, deal.maturityDate, prinStep);
        int nPrin = Math.max(prinDates.size(), 1);
        double principalInstallment = "LINEAR".equalsIgnoreCase(deal.amortizationType)
                ? principal / nPrin
                : 0.0;

        // Interest schedule
        LocalDate intStart = deal.intPayStart != null ? deal.intPayStart : deal.dealDate;
        Period intStep = parseFreq(deal.intPayFreq);
        List<LocalDate> intDates = schedule(intStart, deal.maturityDate, intStep);

        // Walk time: track outstanding principal as principal payments fall.
        double outstanding = principal;
        LocalDate lastAccrual = deal.dealDate;

        // Merge schedules by date order and emit cashflows that fall in the processing window.
        List<LocalDate> allDates = new ArrayList<>(prinDates);
        allDates.addAll(intDates);
        allDates.sort(LocalDate::compareTo);

        for (LocalDate d : allDates) {
            boolean isInterest = intDates.contains(d);
            boolean isPrincipal = prinDates.contains(d);

            if (isInterest) {
                double yf = yearFraction(lastAccrual == null ? d : lastAccrual, d);
                double interest = outstanding * rate * yf;
                lastAccrual = d;
                if (inWindow(d, s)) {
                    out.add(buildCashflow(deal, "INTEREST", d, interest,
                            deal.currency, outstanding, 0.0, outstanding, rate, md, s));
                }
            }
            if (isPrincipal && principalInstallment > 0) {
                outstanding = Math.max(0.0, outstanding - principalInstallment);
                if (inWindow(d, s)) {
                    out.add(buildCashflow(deal, "PRINCIPAL", d, principalInstallment,
                            deal.currency, outstanding, 0.0, outstanding, rate, md, s));
                }
            }
        }

        // Final principal repayment (bullet) if no amortisation configured.
        if (principalInstallment == 0 && inWindow(deal.maturityDate, s)) {
            out.add(buildCashflow(deal, "MATURITY", deal.maturityDate, principal,
                    deal.currency, 0.0, 0.0, 0.0, rate, md, s));
        }
        return out;
    }

    private List<LocalDate> schedule(LocalDate start, LocalDate end, Period step) {
        List<LocalDate> dates = new ArrayList<>();
        if (start == null || end == null) return dates;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            dates.add(d);
            d = d.plus(step);
        }
        return dates;
    }

    private Cashflow buildCashflow(Deal deal, String type, LocalDate date, double amount,
                                   String ccy, double remaining, double accrued, double bv,
                                   double rate, MarketDataService md, ProcessingSettings s) {
        Cashflow c = new Cashflow();
        c.deal = deal.id;
        c.type = type;
        c.date = date;
        c.amount = amount;
        c.currency = ccy;
        c.remainingPrincipal = remaining;
        c.accruedInterest = accrued;
        c.bookValue = bv;
        c.nominalRate = rate;
        double zero = md.curveRate(discountCurveName(deal), date);
        LocalDate asOf = s.processingStartDate != null ? s.processingStartDate : date;
        c.discountFactor = discountFactor(zero, asOf, date);
        c.npv = amount * c.discountFactor;
        return c;
    }

    private String discountCurveName(Deal deal) {
        if (deal.discountCurve != null) return deal.discountCurve;
        return deal.currency == null ? null : deal.currency + "_DISCOUNT";
    }
}
