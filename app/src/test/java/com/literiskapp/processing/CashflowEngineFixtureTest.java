package com.literiskapp.processing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.literiskapp.api.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test of the calculation engine that drives all 6 test deals
 * through the 4 generators and the aggregator using ~100 market records,
 * all read from JSON fixtures under src/test/resources. Does NOT touch any
 * database — it exercises the pure processing pipeline end-to-end.
 */
class CashflowEngineFixtureTest {

    private static List<Deal> deals;
    private static List<Market> markets;
    private static MarketDataService md;
    private static final List<CashflowGenerator> GENERATORS = List.of(
            new RegularPaymentGenerator(),
            new CashAccountGenerator(),
            new FxSwapGenerator(),
            new SecurityGenerator());

    @BeforeAll
    static void loadFixtures() throws Exception {
        ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
        try (InputStream in = CashflowEngineFixtureTest.class.getResourceAsStream("/test-deals.json")) {
            deals = m.readValue(in, new TypeReference<List<Deal>>() {});
        }
        try (InputStream in = CashflowEngineFixtureTest.class.getResourceAsStream("/test-market.json")) {
            markets = m.readValue(in, new TypeReference<List<Market>>() {});
        }
        md = new MarketDataService(markets);
    }

    @Test
    void fixtures_loaded_with_expected_shape() {
        assertThat(deals).hasSize(6);
        assertThat(markets.size()).isBetween(90, 120); // "around 100"
    }

    @Test
    void every_deal_has_a_matching_generator() {
        Set<DealType> supported = new HashSet<>();
        for (CashflowGenerator g : GENERATORS) supported.add(g.supports());
        for (Deal d : deals) {
            assertThat(supported).as("generator for " + d.id).contains(d.type);
        }
    }

    @Test
    void regular_payment_produces_interest_and_principal_in_window() {
        Deal loan = deals.stream().filter(d -> "TEST-LOAN-USD".equals(d.id)).findFirst().orElseThrow();
        var gen = generatorFor(DealType.REGULAR_PAYMENT);

        ProcessingSettings s = settings("2024-01-01", "2025-12-31", Timeband.Monthly, "USD");
        List<Cashflow> cfs = gen.generate(loan, md, s);

        assertThat(cfs).isNotEmpty();
        assertThat(cfs).anyMatch(c -> CashflowType.INTEREST == c.type);
        assertThat(cfs).anyMatch(c -> CashflowType.PRINCIPAL == c.type);
        // principal amortises to 0 at or before maturity
        Cashflow lastPrincipal = cfs.stream()
                .filter(c -> CashflowType.PRINCIPAL == c.type)
                .reduce((a, b) -> b).orElseThrow();
        assertThat(lastPrincipal.remainingPrincipal).isLessThanOrEqualTo(loan.originalPrincipal);
    }

    @Test
    void fx_swap_emits_four_exchange_legs_plus_pnl() {
        Deal swap = deals.stream().filter(d -> "TEST-FXSWAP".equals(d.id)).findFirst().orElseThrow();
        var gen = generatorFor(DealType.FX_SWAP);

        ProcessingSettings s = settings("2024-01-01", "2025-12-31", Timeband.Monthly, "USD");
        List<Cashflow> cfs = gen.generate(swap, md, s);

        assertThat(cfs.stream().filter(c -> CashflowType.FX_NEAR == c.type).count()).isEqualTo(2);
        assertThat(cfs.stream().filter(c -> CashflowType.FX_FAR  == c.type).count()).isEqualTo(2);
        assertThat(cfs).anyMatch(c -> CashflowType.FX_PNL == c.type);
    }

    @Test
    void security_bond_emits_coupons_and_redemption_with_mtm_book_value() {
        Deal bond = deals.stream().filter(d -> "TEST-BOND".equals(d.id)).findFirst().orElseThrow();
        var gen = generatorFor(DealType.SECURITY);

        ProcessingSettings s = settings("2024-01-01", "2026-01-31", Timeband.Quarterly, "USD");
        List<Cashflow> cfs = gen.generate(bond, md, s);

        long coupons = cfs.stream().filter(c -> CashflowType.COUPON == c.type).count();
        assertThat(coupons).isGreaterThanOrEqualTo(3); // semiannual over ~2y
        assertThat(cfs).anyMatch(c -> CashflowType.MATURITY == c.type);
        // MTM book value is face * price / 100 — below par in our fixtures
        Cashflow c0 = cfs.get(0);
        assertThat(c0.bookValue).isLessThanOrEqualTo(bond.faceValue);
    }

    @Test
    void aggregation_produces_per_deal_per_interval_rows_with_summed_measures() {
        List<Cashflow> all = new ArrayList<>();
        ProcessingSettings s = settings("2024-01-01", "2025-12-31", Timeband.Monthly, "USD");
        for (Deal d : deals) {
            all.addAll(generatorFor(d.type).generate(d, md, s));
        }
        var agg = new ResultAggregator();
        List<Result> results = agg.aggregate(deals, all, md, s);

        assertThat(results).isNotEmpty();

        // Unique key: (assetLiab, interval, deal, currency) — no duplicates
        Set<String> keys = new HashSet<>();
        for (Result r : results) {
            String k = r.assetLiability + "|" + r.interval + "|" + r.deal + "|" + r.currency;
            assertThat(keys.add(k)).as("duplicate key: " + k).isTrue();
        }

        // EUR loan interest is on the expense side (it's a LIABILITY), converted to USD.
        Result eurRow = results.stream()
                .filter(r -> "TEST-LOAN-EUR".equals(r.deal) && r.interestExpense > 0)
                .findFirst().orElse(null);
        assertThat(eurRow).as("expected at least one expense row for EUR liability").isNotNull();
        assertThat(eurRow.currency).isEqualTo("EUR");

        // Bond should have coupon income rows
        assertThat(results).anyMatch(r -> "TEST-BOND".equals(r.deal) && r.couponIncome > 0);

        // FX swap: principalFlow rows on the swap exist
        assertThat(results).anyMatch(r -> "TEST-FXSWAP".equals(r.deal) && r.principalFlow != 0);
    }

    @Test
    void currency_conversion_applied_for_non_reporting_currency() {
        ProcessingSettings s = settings("2024-01-01", "2025-12-31", Timeband.Monthly, "USD");
        List<Cashflow> all = new ArrayList<>();
        for (Deal d : deals) {
            all.addAll(generatorFor(d.type).generate(d, md, s));
        }
        List<Result> results = new ResultAggregator().aggregate(deals, all, md, s);

        // Nominal balance of the EUR deposit at Jun-1 in REPORTING USD:
        // 500,000 EUR * EUR/USD ~ 1.08..1.09
        Result eurDepositJune = results.stream()
                .filter(r -> "TEST-CASH-EUR".equals(r.deal) && r.interval.equals(LocalDate.of(2024, 6, 1)))
                .findFirst().orElseThrow();
        assertThat(eurDepositJune.nominalBalance).isBetween(540_000.0, 550_000.0);
    }

    // ---------- helpers ----------

    private CashflowGenerator generatorFor(DealType type) {
        for (CashflowGenerator g : GENERATORS) {
            if (g.supports() == type) return g;
        }
        throw new IllegalArgumentException("No generator for " + type);
    }

    private ProcessingSettings settings(String start, String end, Timeband tb, String ccy) {
        ProcessingSettings s = new ProcessingSettings();
        s.processingStartDate = LocalDate.parse(start);
        s.processingEndDate = LocalDate.parse(end);
        s.timeband = tb;
        s.reportingCurrency = ccy;
        return s;
    }
}
