package com.example.policypremium.rules;

import com.example.policypremium.domain.CoverageType;
import com.example.policypremium.domain.Region;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The pricing policy: base rates, rating factors and the premium floor.
 *
 * <p>Kept separate from the domain enums so the rates can change without touching the
 * domain vocabulary, and so the whole table is readable in one place.
 *
 * <p>Every band is <strong>inclusive of its lower bound and exclusive of its upper</strong>.
 * A sum insured of exactly 500 000 therefore falls in the 500k-1M band, not the one below.
 * Boundary behaviour is the most common source of pricing disputes, so it is stated here
 * and pinned by tests rather than left to the reader.
 */
public final class RateTable {

    /** ISO 4217 code for all amounts this service produces. */
    public static final String CURRENCY = "SEK";

    /**
     * No premium is ever quoted below this, however favourable the factors.
     *
     * <p>Set above the cheapest combination the table can produce (LIABILITY in NORTH, in the
     * lowest band, with no prior claims, prices at 720) so the floor actually engages at the
     * bottom of the book rather than being a rule that never fires.
     */
    public static final BigDecimal MINIMUM_PREMIUM = new BigDecimal("750.00");

    /** Prior-claims loading is capped at this many claims; anything higher rates the same. */
    public static final int CLAIMS_CAP = 4;

    private static final Map<CoverageType, BigDecimal> BASE_RATES;
    private static final Map<Region, BigDecimal> REGION_FACTORS;
    private static final NavigableMap<BigDecimal, BigDecimal> SUM_INSURED_BANDS;
    private static final Map<Integer, BigDecimal> CLAIMS_LOADINGS;

    static {
        Map<CoverageType, BigDecimal> baseRates = new EnumMap<>(CoverageType.class);
        baseRates.put(CoverageType.HOME, new BigDecimal("1200"));
        baseRates.put(CoverageType.AUTO, new BigDecimal("2400"));
        baseRates.put(CoverageType.LIABILITY, new BigDecimal("800"));
        BASE_RATES = Collections.unmodifiableMap(baseRates);

        Map<Region, BigDecimal> regionFactors = new EnumMap<>(Region.class);
        regionFactors.put(Region.STOCKHOLM, new BigDecimal("1.25"));
        regionFactors.put(Region.GOTEBORG, new BigDecimal("1.15"));
        regionFactors.put(Region.MALMO, new BigDecimal("1.10"));
        regionFactors.put(Region.NORTH, new BigDecimal("0.90"));
        regionFactors.put(Region.OTHER, new BigDecimal("1.00"));
        REGION_FACTORS = Collections.unmodifiableMap(regionFactors);

        // Keys are inclusive lower bounds; floorEntry gives the band a value falls into.
        NavigableMap<BigDecimal, BigDecimal> bands = new TreeMap<>();
        bands.put(BigDecimal.ZERO, new BigDecimal("1.00"));
        bands.put(new BigDecimal("500000"), new BigDecimal("1.15"));
        bands.put(new BigDecimal("1000000"), new BigDecimal("1.35"));
        bands.put(new BigDecimal("5000000"), new BigDecimal("1.60"));
        SUM_INSURED_BANDS = Collections.unmodifiableNavigableMap(bands);

        CLAIMS_LOADINGS = Map.of(
                0, new BigDecimal("1.00"),
                1, new BigDecimal("1.20"),
                2, new BigDecimal("1.45"),
                3, new BigDecimal("1.75"),
                4, new BigDecimal("2.00"));
    }

    private RateTable() {
        // Static rate lookups only; not intended for instantiation.
    }

    /** Annual base rate for the kind of cover, before any rating factors. */
    public static BigDecimal baseRate(CoverageType coverageType) {
        return require(BASE_RATES.get(coverageType), "base rate", coverageType);
    }

    /** Multiplier reflecting regional risk. */
    public static BigDecimal regionFactor(Region region) {
        return require(REGION_FACTORS.get(region), "region factor", region);
    }

    /**
     * Multiplier for the amount insured. Bands are inclusive of their lower bound and
     * exclusive of their upper.
     */
    public static BigDecimal sumInsuredFactor(BigDecimal sumInsured) {
        if (sumInsured == null || sumInsured.signum() <= 0) {
            throw new IllegalArgumentException("sumInsured must be greater than zero");
        }
        return SUM_INSURED_BANDS.floorEntry(sumInsured).getValue();
    }

    /** Multiplier for prior claims, capped at {@link #CLAIMS_CAP} claims. */
    public static BigDecimal claimsLoading(int priorClaimsCount) {
        if (priorClaimsCount < 0) {
            throw new IllegalArgumentException("priorClaimsCount must not be negative");
        }
        return CLAIMS_LOADINGS.get(Math.min(priorClaimsCount, CLAIMS_CAP));
    }

    private static BigDecimal require(BigDecimal value, String what, Object key) {
        if (value == null) {
            throw new IllegalArgumentException("No " + what + " configured for " + key);
        }
        return value;
    }
}
