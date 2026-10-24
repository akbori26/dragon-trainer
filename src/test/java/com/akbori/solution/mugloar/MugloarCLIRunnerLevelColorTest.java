package com.akbori.solution.mugloar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The level color fades from yellow at level 1 to green at level 5 and stays green above it.
 */
class MugloarCLIRunnerLevelColorTest {

    @ParameterizedTest(name = "level {0} -> color {1}")
    @CsvSource({
            "0,   226",  // new game: yellow
            "1,   226",  // yellow
            "2,   190",
            "3,   154",
            "4,   82",
            "5,   46",   // green
            "6,   46",
            "42,  46"
    })
    void levelColorFadesFromYellowToGreen(int level, int expectedCode) {
        assertThat(MugloarCLIRunner.levelColorCode(level)).isEqualTo(expectedCode);
    }
}
