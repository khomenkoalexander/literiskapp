package com.literiskapp.processing;

import com.literiskapp.api.Market;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * In-memory lookup of market data with linear interpolation between observed
 * dates and flat extrapolation beyond the bounds. Built per processing run
 * from the full set of market records.
 *
 * <p>FX quote convention: object "X/Y" = price of 1 X in Y. To convert an
 * amount in X to Y, multiply by the X/Y rate. If neither direction is quoted
 * directly, the inverse (Y/X) is used and reciprocated.
 */
public class MarketDataService {

    /** type -> object -> (date-sorted) observations. */
    private final Map<String, Map<String, NavigableMap<LocalDate, Double>>> byTypeObject = new HashMap<>();

    public MarketDataService(List<Market> markets) {
        for (Market m : markets) {
            if (m.type == null || m.object == null || m.date == null || m.dvalue == null) continue;
            byTypeObject
                .computeIfAbsent(m.type.toUpperCase(Locale.ROOT), k -> new HashMap<>())
                .computeIfAbsent(m.object, k -> new TreeMap<>())
                .put(m.date, m.dvalue);
        }
    }

    /** Rate to convert 1 unit of {@code from} into {@code to} on the given date. */
    public double fxRate(String from, String to, LocalDate date) {
        if (from == null || to == null) return 1.0;
        if (from.equalsIgnoreCase(to)) return 1.0;
        Double direct = lookup("FX", from + "/" + to, date);
        if (direct != null) return direct;
        Double inverse = lookup("FX", to + "/" + from, date);
        if (inverse != null && inverse != 0.0) return 1.0 / inverse;
        return 1.0; // no data: assume parity rather than crash the run
    }

    /** Curve/zero rate for the named curve on the given date. */
    public double curveRate(String curveName, LocalDate date) {
        if (curveName == null) return 0.0;
        Double v = lookup("CURVE", curveName, date);
        return v == null ? 0.0 : v;
    }

    /** Market price for a priced object (e.g. bond price) on the given date. */
    public double price(String priceObject, LocalDate date) {
        if (priceObject == null) return 100.0; // par by default
        Double v = lookup("PRICE", priceObject, date);
        return v == null ? 100.0 : v;
    }

    /** Generic interest-rate lookup (type = INTEREST_RATE). */
    public double interestRate(String object, LocalDate date) {
        Double v = lookup("INTEREST_RATE", object, date);
        return v == null ? 0.0 : v;
    }

    /**
     * Core interpolation: exact match returns the value; otherwise linear
     * interpolation between surrounding dates; flat extrapolation beyond bounds.
     */
    private Double lookup(String type, String object, LocalDate date) {
        var perObject = byTypeObject.get(type == null ? null : type.toUpperCase(Locale.ROOT));
        if (perObject == null) return null;
        NavigableMap<LocalDate, Double> points = perObject.get(object);
        if (points == null || points.isEmpty()) return null;

        Double exact = points.get(date);
        if (exact != null) return exact;

        Map.Entry<LocalDate, Double> lo = points.floorEntry(date);
        Map.Entry<LocalDate, Double> hi = points.ceilingEntry(date);

        if (lo == null) return hi.getValue();       // extrapolate flat at start
        if (hi == null) return lo.getValue();       // extrapolate flat at end

        double days = ChronoUnit.DAYS.between(lo.getKey(), hi.getKey());
        if (days == 0) return lo.getValue();
        double w = ChronoUnit.DAYS.between(lo.getKey(), date) / days;
        return lo.getValue() + w * (hi.getValue() - lo.getValue());
    }
}
