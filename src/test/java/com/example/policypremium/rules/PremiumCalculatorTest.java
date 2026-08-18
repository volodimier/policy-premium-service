package com.example.policypremium.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.example.policypremium.domain.CoverageType;
import com.example.policypremium.domain.PolicyAttributes;
import com.example.policypremium.domain.PremiumBreakdown;
import com.example.policypremium.domain.PremiumCalculation;
import com.example.policypremium.domain.Region;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Rules tests. These run with no Spring context - the calculator is a pure function, so
 * exhaustive cases cost milliseconds.
 */
class PremiumCalculatorTest {

    private static PremiumCalculation calculate(CoverageType type, Region region, String sumInsured, int claims) {
        return PremiumCalculator.calculate(new PolicyAttributes(type, new BigDecimal(sumInsured), region, claims));
    }

    @Nested
    @DisplayName("worked examples")
    class WorkedExamples {

        @ParameterizedTest(name = "{0} in {1}, {2} insured, {3} claims -> {4}")
        @CsvSource({
            "HOME,STOCKHOLM,300000,0,1500.00",
            "AUTO,MALMO,750000,2,4402.20",
            "LIABILITY,OTHER,1000000,1,1296.00",
            "AUTO,STOCKHOLM,6000000,4,9600.00",
            "HOME,NORTH,5000000,3,3024.00"
        })
        void priceAsPublished(CoverageType type, Region region, String sumInsured, int claims, BigDecimal expected) {
            assertThat(calculate(type, region, sumInsured, claims).premium()).isEqualByComparingTo(expected);
        }
    }

    @Nested
    @DisplayName("sum insured bands")
    class Bands {

        @Test
        void changeThePremiumAtTheBoundary() {
            // 499999.99 sits in the lowest band; 500000 is the inclusive lower bound of the next.
            assertThat(calculate(CoverageType.HOME, Region.GOTEBORG, "499999.99", 1)
                            .premium())
                    .isEqualByComparingTo(new BigDecimal("1656.00"));
            assertThat(calculate(CoverageType.HOME, Region.GOTEBORG, "500000", 1)
                            .premium())
                    .isEqualByComparingTo(new BigDecimal("1904.40"));
        }

        @ParameterizedTest
        @CsvSource({"499999.99,1.00", "500000,1.15", "1000000,1.35", "5000000,1.60"})
        void areReflectedInTheBreakdown(String sumInsured, BigDecimal expectedFactor) {
            assertThat(calculate(CoverageType.HOME, Region.OTHER, sumInsured, 0)
                            .breakdown()
                            .sumInsuredFactor())
                    .isEqualByComparingTo(expectedFactor);
        }
    }

    @Nested
    @DisplayName("prior claims")
    class PriorClaims {

        @ParameterizedTest
        @CsvSource({"0,1.00", "1,1.20", "2,1.45", "3,1.75", "4,2.00"})
        void loadThePremium(int claims, BigDecimal expectedLoading) {
            assertThat(calculate(CoverageType.AUTO, Region.OTHER, "100000", claims)
                            .breakdown()
                            .claimsLoading())
                    .isEqualByComparingTo(expectedLoading);
        }

        @ParameterizedTest
        @CsvSource({"5", "12", "100"})
        void areCappedSoTheWorstRecordPaysTheSame(int claims) {
            BigDecimal atCap = calculate(CoverageType.AUTO, Region.OTHER, "100000", RateTable.CLAIMS_CAP)
                    .premium();
            assertThat(calculate(CoverageType.AUTO, Region.OTHER, "100000", claims)
                            .premium())
                    .isEqualByComparingTo(atCap);
        }
    }

    @Nested
    @DisplayName("minimum premium floor")
    class MinimumPremium {

        @Test
        void raisesTheCheapestCombinationAndFlagsIt() {
            // LIABILITY in NORTH, lowest band, no claims prices at 720 - the only combination
            // the table can produce below the floor.
            PremiumCalculation result = calculate(CoverageType.LIABILITY, Region.NORTH, "100000", 0);

            assertThat(result.premium()).isEqualByComparingTo(new BigDecimal("750.00"));
            assertThat(result.breakdown().calculatedPremium()).isEqualByComparingTo(new BigDecimal("720.00"));
            assertThat(result.breakdown().minimumPremiumApplied()).isTrue();
        }

        @Test
        void isNotFlaggedWhenTheCalculationClearsIt() {
            PremiumCalculation result = calculate(CoverageType.AUTO, Region.STOCKHOLM, "100000", 0);

            assertThat(result.breakdown().minimumPremiumApplied()).isFalse();
            assertThat(result.premium()).isEqualByComparingTo(result.breakdown().calculatedPremium());
        }

        @Test
        void neverQuotesBelowTheFloor() {
            for (CoverageType type : CoverageType.values()) {
                for (Region region : Region.values()) {
                    assertThat(calculate(type, region, "1", 0).premium())
                            .isGreaterThanOrEqualTo(RateTable.MINIMUM_PREMIUM);
                }
            }
        }
    }

    @Nested
    @DisplayName("money handling")
    class MoneyHandling {

        /**
         * Every quoted premium is returned at exactly 2dp, so 1667.5 is presented as 1667.50.
         *
         * <p>The rounding mode itself is defensive: the current table produces a finite set of
         * premiums that all terminate at or before 2dp, so HALF_UP never changes a value. It
         * starts to matter the moment rates stop being whole hundredths.
         */
        @ParameterizedTest
        @EnumSource(CoverageType.class)
        void quotesAlwaysCarryTwoDecimalPlaces(CoverageType type) {
            for (Region region : Region.values()) {
                for (String sumInsured : new String[] {"100000", "500000", "1000000", "5000000"}) {
                    for (int claims = 0; claims <= 5; claims++) {
                        assertThat(calculate(type, region, sumInsured, claims)
                                        .premium()
                                        .scale())
                                .isEqualTo(2);
                    }
                }
            }
        }

        @Test
        void reportTheCurrencyOfTheRateTable() {
            assertThat(calculate(CoverageType.HOME, Region.OTHER, "100000", 0).currency())
                    .isEqualTo(RateTable.CURRENCY);
        }
    }

    @Nested
    @DisplayName("breakdown")
    class Breakdown {

        @Test
        void exposesEveryFactorThatProducedThePremium() {
            PremiumBreakdown breakdown =
                    calculate(CoverageType.AUTO, Region.MALMO, "750000", 2).breakdown();

            assertThat(breakdown.baseRate()).isEqualByComparingTo(new BigDecimal("2400"));
            assertThat(breakdown.regionFactor()).isEqualByComparingTo(new BigDecimal("1.10"));
            assertThat(breakdown.sumInsuredFactor()).isEqualByComparingTo(new BigDecimal("1.15"));
            assertThat(breakdown.claimsLoading()).isEqualByComparingTo(new BigDecimal("1.45"));
            assertThat(breakdown.calculatedPremium()).isEqualByComparingTo(new BigDecimal("4402.20"));
        }

        @Test
        void multipliesOutToTheQuotedPremium() {
            PremiumCalculation result = calculate(CoverageType.HOME, Region.STOCKHOLM, "2000000", 3);
            PremiumBreakdown b = result.breakdown();

            assertThat(b.baseRate()
                            .multiply(b.regionFactor())
                            .multiply(b.sumInsuredFactor())
                            .multiply(b.claimsLoading()))
                    .isEqualByComparingTo(result.premium());
        }
    }

    @Test
    void rejectsNullAttributes() {
        assertThatNullPointerException().isThrownBy(() -> PremiumCalculator.calculate(null));
    }
}
