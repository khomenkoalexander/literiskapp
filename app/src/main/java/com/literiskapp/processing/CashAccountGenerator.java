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
 * Cash deposit / call account: accrues interest at {@code nominalRate} on the
 * original principal at {@code intPayFreq} (monthly if unset); principal bullet
 * returned at maturity.
 */
@Component
public class CashAccountGenerator implements CashflowGenerator {

    @Override public String supports() { return "CASH_ACCOUNT"; }

    @Override
    public List<Cashflow> generate(Deal deal, MarketDataService md, ProcessingSettings s) {
        List<Cashflow> out = new ArrayList<>();
        if (deal.originalPrincipal == null || deal.maturityDate == null) return out;

        double balance = deal.originalPrincipal;
        double rate = deal.nominalRate == null ? 0.0 : deal.nominalRate;

        Period step = parseFreq(deal.intPayFreq != null ? deal.intPayFreq : deal.prinPayFreq);
        LocalDate cur = deal.dealDate != null ? deal.dealDate.plus(step) : null;
        LocalDate lastAccrual = deal.dealDate;

        while (cur != null && !cur.isAfter(deal.maturityDate)) {
            double yf = yearFraction(lastAccrual, cur);
            double interest = balance * rate * yf;
            if (inWindow(cur, s)) {
                out.add(build(deal, "INTEREST", cur, interest, balance, rate, md, s));
            }
            lastAccrual = cur;
            cur = cur.plus(step);
        }

        // Final accrual stub to maturity if last coupon date != maturity
        if (lastAccrual != null && !lastAccrual.equals(deal.maturityDate)) {
            double stub = balance * rate * yearFraction(lastAccrual, deal.maturityDate);
            if (stub != 0 && inWindow(deal.maturityDate, s)) {
                out.add(build(deal, "INTEREST", deal.maturityDate, stub, balance, rate, md, s));
            }
        }

        // Principal repayment at maturity
        if (inWindow(deal.maturityDate, s)) {
            out.add(build(deal, "MATURITY", deal.maturityDate, balance, 0.0, rate, md, s));
        }
        return out;
    }

    private Cashflow build(Deal deal, String type, LocalDate date, double amount,
                           double bvAfter, double rate, MarketDataService md, ProcessingSettings s) {
        Cashflow c = new Cashflow();
        c.deal = deal.id;
        c.type = type;
        c.date = date;
        c.amount = amount;
        c.currency = deal.currency;
        c.remainingPrincipal = bvAfter;
        c.accruedInterest = 0.0;
        c.bookValue = bvAfter;
        c.nominalRate = rate;
        String curve = deal.discountCurve != null
                ? deal.discountCurve
                : (deal.currency == null ? null : deal.currency + "_DISCOUNT");
        double zero = md.curveRate(curve, date);
        LocalDate asOf = s.processingStartDate != null ? s.processingStartDate : date;
        c.discountFactor = discountFactor(zero, asOf, date);
        c.npv = amount * c.discountFactor;
        return c;
    }
}
