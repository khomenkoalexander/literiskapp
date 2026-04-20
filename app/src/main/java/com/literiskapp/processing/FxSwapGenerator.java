package com.literiskapp.processing;

import com.literiskapp.api.Cashflow;
import com.literiskapp.api.Deal;
import com.literiskapp.api.ProcessingSettings;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * FX swap: two legs each producing an exchange of currencies.
 *
 * <p>Near date: pay NEAR currency, receive FAR currency (at agreed spot).
 * Far date:  receive NEAR currency, pay FAR currency (at agreed forward).
 *
 * <p>MTM P&amp;L is carried on the far-date leg as the delta between the agreed
 * forward rate and the current market forward implied by spot, stored as an
 * {@code FX_PNL}-typed cashflow at the far date in the near-leg currency.
 */
@Component
public class FxSwapGenerator implements CashflowGenerator {

    @Override public String supports() { return "FX_SWAP"; }

    @Override
    public List<Cashflow> generate(Deal deal, MarketDataService md, ProcessingSettings s) {
        List<Cashflow> out = new ArrayList<>();
        if (deal.fxNearDate == null || deal.fxFarDate == null) return out;

        // Near leg: -near amount in near ccy, +far amount in far ccy
        if (inWindow(deal.fxNearDate, s)) {
            out.add(build(deal, "FX_NEAR", deal.fxNearDate,
                    -nz(deal.fxNearAmount), deal.fxNearCurrency, md, s));
            out.add(build(deal, "FX_NEAR", deal.fxNearDate,
                    nz(deal.fxFarAmount), deal.fxFarCurrency, md, s));
        }

        // Far leg: +near amount in near ccy, -far amount in far ccy
        if (inWindow(deal.fxFarDate, s)) {
            out.add(build(deal, "FX_FAR", deal.fxFarDate,
                    nz(deal.fxNearAmount), deal.fxNearCurrency, md, s));
            out.add(build(deal, "FX_FAR", deal.fxFarDate,
                    -nz(deal.fxFarAmount), deal.fxFarCurrency, md, s));

            // MTM P&L: agreed forward vs market spot at far date, on the near notional.
            double marketFwd = md.fxRate(deal.fxNearCurrency, deal.fxFarCurrency, deal.fxFarDate);
            double agreedFwd = deal.fxForwardRate == null ? marketFwd : deal.fxForwardRate;
            double pnlInFar = nz(deal.fxNearAmount) * (marketFwd - agreedFwd);
            out.add(build(deal, "FX_PNL", deal.fxFarDate, pnlInFar, deal.fxFarCurrency, md, s));
        }
        return out;
    }

    private double nz(Double d) { return d == null ? 0.0 : d; }

    private Cashflow build(Deal deal, String type, LocalDate date, double amount,
                           String ccy, MarketDataService md, ProcessingSettings s) {
        Cashflow c = new Cashflow();
        c.deal = deal.id;
        c.type = type;
        c.date = date;
        c.amount = amount;
        c.currency = ccy;
        c.remainingPrincipal = 0.0;
        c.accruedInterest = 0.0;
        c.bookValue = 0.0;
        c.nominalRate = 0.0;
        String curve = ccy == null ? null : ccy + "_DISCOUNT";
        double zero = md.curveRate(curve, date);
        LocalDate asOf = s.processingStartDate != null ? s.processingStartDate : date;
        c.discountFactor = discountFactor(zero, asOf, date);
        c.npv = amount * c.discountFactor;
        return c;
    }
}
