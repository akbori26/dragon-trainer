package com.akbori.solution.mugloar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mapRiskCategory() turns the server's probability text into a risk label, safest to deadliest.
 */
class MugloarCLIRunnerRiskCategoryTest {

    // mapRiskCategory() uses none of the collaborators
    private final MugloarCLIRunner runner = new MugloarCLIRunner(null, null, null, null, null, null);

    private String label(String probability) {
        return ReflectionTestUtils.invokeMethod(runner, "mapRiskCategory", probability);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Piece of cake      | 🟢 SAFE",
            "Sure thing         | 🟢 SAFE",
            "Walk in the park   | 🟢 SAFE",
            "Quite likely       | 🟡 LIKELY",
            "Hmmm....           | 🟡 LIKELY",
            "Gamble             | 🎲 GAMBLE",
            "Risky              | 🟠 RISKY",
            "Playing with fire  | 🟠 RISKY",
            "Rather detrimental | 🔴 DANGEROUS",
            "Suicide mission    | ☠️ FATAL",
            "Impossible         | ☠️ FATAL",
            // Matching ignores case
            "PIECE OF CAKE      | 🟢 SAFE",
            "suicide MISSION    | ☠️ FATAL"
    })
    void knownProbabilitiesMapToTheirCategory(String probability, String expectedLabel) {
        assertThat(label(probability)).isEqualTo(expectedLabel);
    }

    @Test
    void unknownProbabilityIsShownAsIsInUpperCase() {
        assertThat(label("Totally new text")).isEqualTo("⚪ TOTALLY NEW TEXT");
    }

    @Test
    void missingProbabilityIsUnknownRisk() {
        assertThat(label(null)).isEqualTo("UNKNOWN RISK");
    }
}