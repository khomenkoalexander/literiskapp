package com.literiskapp.processing;

import com.literiskapp.api.Market;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MarketDataServiceTest {

    private Market fx(String obj, String date, double v) {
        Market m = new Market();
        m.type = "FX"; m.object = obj; m.date = LocalDate.parse(date); m.dvalue = v;
        return m;
    }

    private Market curve(String obj, String date, double v) {
        Market m = new Market();
        m.type = "CURVE"; m.object = obj; m.date = LocalDate.parse(date); m.dvalue = v;
        return m;
    }

    @Test
    void fx_rate_uses_direct_quote_when_available() {
        var md = new MarketDataService(List.of(fx("EUR/USD", "2024-01-01", 1.08)));
        assertThat(md.fxRate("EUR", "USD", LocalDate.parse("2024-01-01"))).isEqualTo(1.08);
    }

    @Test
    void fx_rate_inverts_when_only_reverse_quote_exists() {
        var md = new MarketDataService(List.of(fx("EUR/USD", "2024-01-01", 1.25)));
        assertThat(md.fxRate("USD", "EUR", LocalDate.parse("2024-01-01")))
                .isCloseTo(0.8, within(1e-9));
    }

    @Test
    void fx_same_currency_returns_one() {
        var md = new MarketDataService(List.of());
        assertThat(md.fxRate("USD", "USD", LocalDate.parse("2024-06-15"))).isEqualTo(1.0);
    }

    @Test
    void linear_interpolation_between_two_dates() {
        // Halfway between Jan 1 (0.05) and Apr 1 (0.06) should be ~0.055
        var md = new MarketDataService(List.of(
                curve("USD_DISCOUNT", "2024-01-01", 0.05),
                curve("USD_DISCOUNT", "2024-04-01", 0.06)
        ));
        LocalDate mid = LocalDate.of(2024, 2, 15); // roughly middle
        double v = md.curveRate("USD_DISCOUNT", mid);
        // 45 days out of 91 days
        double expected = 0.05 + (45.0 / 91.0) * (0.06 - 0.05);
        assertThat(v).isCloseTo(expected, within(1e-6));
    }

    @Test
    void flat_extrapolation_at_boundaries() {
        var md = new MarketDataService(List.of(
                curve("USD_DISCOUNT", "2024-01-01", 0.05),
                curve("USD_DISCOUNT", "2024-12-01", 0.04)
        ));
        // before first: flat at first
        assertThat(md.curveRate("USD_DISCOUNT", LocalDate.of(2023, 6, 1))).isEqualTo(0.05);
        // after last: flat at last
        assertThat(md.curveRate("USD_DISCOUNT", LocalDate.of(2025, 6, 1))).isEqualTo(0.04);
    }

    @Test
    void missing_object_returns_sensible_default() {
        var md = new MarketDataService(List.of());
        assertThat(md.curveRate("UNKNOWN", LocalDate.now())).isEqualTo(0.0);
        assertThat(md.price("UNKNOWN_PRICE", LocalDate.now())).isEqualTo(100.0); // par default
    }
}
