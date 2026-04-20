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
 * Fixed-coupon bond: coupon stream on {@code couponFreq} schedule plus face
 * value at maturity. Book value is marked to market using {@code marketPriceObj}
 * (price quoted as % of face).
 */
@Component
public class SecurityGenerator implements CashflowGenerator {

    @Override public String supports() { return "SECURITY"; }

    @Override
    public List<Cashflow> generate(Deal deal, MarketDataService md, ProcessingSettings s) {
        List<Cashflow> out = new ArrayList<>();
        if (deal.maturityDate == null) return out;
        double face = deal.faceValue != null ? deal.faceValue
                : (deal.originalPrincipal != null ? deal.originalPrincipal : 0.0);
        double cr = deal.couponRate == null ? 0.0 : deal.couponRate;

        Period step = parseFreq(deal.couponFreq);
        LocalDate cur = deal.couponStart != null ? deal.couponStart : deal.dealDate;
        LocalDate lastAccrual = deal.dealDate;

        while (cur != null && !cur.isAfter(deal.maturityDate)) {
            double yf = yearFraction(lastAccrual, cur);
            double coupon = face * cr * yf;
            if (inWindow(cur, s)) {
                out.add(build(deal, "COUPON", cur, coupon, face, cr, md, s));
            }
            lastAccrual = cur;
            cur = cur.plus(step);
        }

        // Face value at maturity
        if (inWindow(deal.maturityDate, s)) {
            out.add(build(deal, "MATURITY", deal.maturityDate, face, 0.0, cr, md, s));
        }
        return out;
    }

    private Cashflow build(Deal deal, String type, LocalDate date, double amount,
                           double remainingFace, double cr, MarketDataService md, ProcessingSettings s) {
        Cashflow c = new Cashflow();
        c.deal = deal.id;
        c.type = type;
        c.date = date;
        c.amount = amount;
        c.currency = deal.currency;
        c.remainingPrincipal = remainingFace;
        c.accruedInterest = 0.0;
        // Mark-to-market book value: face * price / 100
        double priceFactor = md.price(deal.marketPriceObj, date) / 100.0;
        c.bookValue = remainingFace * priceFactor;
        c.nominalRate = cr;
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
