package com.example.policypremium.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.policypremium.domain.CoverageType;
import com.example.policypremium.domain.Region;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins the published rate table. These assertions exist so that a change to pricing is a
 * deliberate act with a failing test attached, not a silent edit.
 */
class RateTableTest {

    @Nested
    @DisplayName("base rates")
    class BaseRates {

        @ParameterizedTest
        @CsvSource({"HOME,1200", "AUTO,2400", "LIABILITY,800"})
        void matchThePublishedTable(CoverageType type, BigDecimal expected) {
            assertThat(RateTable.baseRate(type)).isEqualByComparingTo(expected);
        }

        @ParameterizedTest
        @EnumSource(CoverageType.class)
        void areDefinedForEveryCoverageType(CoverageType type) {
            assertThat(RateTable.baseRate(type)).isNotNull().isPositive();
        }
    }

    @Nested
    @DisplayName("region factors")
    class RegionFactors {

        @ParameterizedTest
        @CsvSource({"STOCKHOLM,1.25", "GOTEBORG,1.15", "MALMO,1.10", "NORTH,0.90", "OTHER,1.00"})
        void matchThePublishedTable(Region region, BigDecimal expected) {
            assertThat(RateTable.regionFactor(region)).isEqualByComparingTo(expected);
        }

        @ParameterizedTest
        @EnumSource(Region.class)
        void areDefinedForEveryRegion(Region region) {
            assertThat(RateTable.regionFactor(region)).isNotNull().isPositive();
        }
    }

    @Nested
    @DisplayName("sum insured bands (inclusive lower, exclusive upper)")
    class SumInsuredBands {

        @ParameterizedTest
        @CsvSource({
            "1,1.00",
            "499999.99,1.00",
            "500000,1.15", // lower bound is inclusive: this is the 500k-1M band
            "999999.99,1.15",
            "1000000,1.35",
            "4999999.99,1.35",
            "5000000,1.60",
            "100000000,1.60"
        })
        void applyAtTheirBoundaries(BigDecimal sumInsured, BigDecimal expected) {
            assertThat(RateTable.sumInsuredFactor(sumInsured)).isEqualByComparingTo(expected);
        }

        @Test
        void rejectNonPositiveAmounts() {
            assertThatThrownBy(() -> RateTable.sumInsuredFactor(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RateTable.sumInsuredFactor(new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("claims loadings")
    class ClaimsLoadings {

        @ParameterizedTest
        @CsvSource({"0,1.00", "1,1.20", "2,1.45", "3,1.75", "4,2.00"})
        void matchThePublishedTable(int claims, BigDecimal expected) {
            assertThat(RateTable.claimsLoading(claims)).isEqualByComparingTo(expected);
        }

        @ParameterizedTest
        @CsvSource({"5", "10", "50"})
        void areCappedAboveFourClaims(int claims) {
            assertThat(RateTable.claimsLoading(claims))
                    .isEqualByComparingTo(RateTable.claimsLoading(RateTable.CLAIMS_CAP));
        }

        @Test
        void rejectNegativeCounts() {
            assertThatThrownBy(() -> RateTable.claimsLoading(-1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void minimumPremiumAndCurrencyArePublished() {
        assertThat(RateTable.MINIMUM_PREMIUM).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(RateTable.CURRENCY).isEqualTo("SEK");
    }
}
