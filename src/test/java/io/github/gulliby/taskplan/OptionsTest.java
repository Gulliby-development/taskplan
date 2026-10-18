package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OptionsTest {
    @ParameterizedTest
    @MethodSource("validOptions")
    void parsesValidOptions(ExpectedOptions expected) {
        Options actual = Options.parse(expected.args());

        assertEquals(expected.command(), actual.command());
        assertEquals(expected.file(), actual.file());
        assertEquals(expected.workers(), actual.workers());
        assertEquals(expected.scale(), actual.scale());
        assertFalse(actual.help());
    }

    static Stream<Arguments> validOptions() {
        return Stream.of(
                new ExpectedOptions(new String[]{"check", "plan.txt"}, "check", "plan.txt", 2, 1),
                new ExpectedOptions(new String[]{"critical", "plan.txt"}, "critical", "plan.txt", 2, 1),
                new ExpectedOptions(new String[]{"schedule", "plan.txt"}, "schedule", "plan.txt", 2, 1),
                new ExpectedOptions(new String[]{"gantt", "plan.txt"}, "gantt", "plan.txt", 2, 1),
                new ExpectedOptions(new String[]{"schedule", "--workers", "1", "plan.txt"}, "schedule", "plan.txt", 1, 1),
                new ExpectedOptions(new String[]{"schedule", "--workers=64", "plan.txt"}, "schedule", "plan.txt", 64, 1),
                new ExpectedOptions(new String[]{"gantt", "--scale", "1", "plan.txt"}, "gantt", "plan.txt", 2, 1),
                new ExpectedOptions(new String[]{"gantt", "--scale=1000", "plan.txt"}, "gantt", "plan.txt", 2, 1000),
                new ExpectedOptions(new String[]{"schedule", "--workers=3", "--workers", "4", "plan.txt"}, "schedule", "plan.txt", 4, 1),
                new ExpectedOptions(new String[]{"schedule", "--workers", "4", "--workers=3", "plan.txt"}, "schedule", "plan.txt", 3, 1),
                new ExpectedOptions(new String[]{"gantt", "--scale=2", "--scale", "7", "plan.txt"}, "gantt", "plan.txt", 2, 7),
                new ExpectedOptions(new String[]{"gantt", "--scale", "7", "--scale=2", "plan.txt"}, "gantt", "plan.txt", 2, 2),
                new ExpectedOptions(new String[]{"schedule", "plan.txt", "--workers", "6"}, "schedule", "plan.txt", 6, 1),
                new ExpectedOptions(new String[]{"gantt", "plan.txt", "--scale=12"}, "gantt", "plan.txt", 2, 12),
                new ExpectedOptions(new String[]{"check", "-"}, "check", "-", 2, 1),
                new ExpectedOptions(new String[]{"critical", "-"}, "critical", "-", 2, 1),
                new ExpectedOptions(new String[]{"schedule", "--workers=2", "plan.txt", "--workers=64"}, "schedule", "plan.txt", 64, 1)
        ).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("invalidOptions")
    void reportsExactArgumentErrors(String[] args, String message) {
        PlanException exception = assertThrows(PlanException.class, () -> Options.parse(args));
        assertEquals(message, exception.getMessage());
    }

    static Stream<Arguments> invalidOptions() {
        return Stream.of(
                error(new String[]{}, "missing command (check, critical, schedule, gantt or order)"),
                error(new String[]{"unknown"}, "unknown command \"unknown\""),
                error(new String[]{"--workers"}, "unknown command \"--workers\""),
                error(new String[]{"--help", "check"}, "unknown option \"--help\""),
                error(new String[]{"--help", "--help"}, "unknown option \"--help\""),
                error(new String[]{"check", "--help"}, "unknown option \"--help\""),
                error(new String[]{"check", "--unknown"}, "unknown option \"--unknown\""),
                error(new String[]{"check", "-v"}, "unknown option \"-v\""),
                error(new String[]{"check", "--workers"}, "--workers needs a value"),
                error(new String[]{"check", "--scale"}, "--scale needs a value"),
                error(new String[]{"schedule", "--workers"}, "--workers needs a value"),
                error(new String[]{"gantt", "--scale"}, "--scale needs a value"),

                error(new String[]{"schedule", "--workers", "-1", "plan.txt"},
                        "invalid --workers \"-1\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers=0", "plan.txt"},
                        "invalid --workers \"0\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers=65", "plan.txt"},
                        "invalid --workers \"65\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers=99999999999", "plan.txt"},
                        "invalid --workers \"99999999999\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers=", "plan.txt"},
                        "invalid --workers \"\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers=01", "plan.txt"},
                        "invalid --workers \"01\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers=+1", "plan.txt"},
                        "invalid --workers \"+1\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers=1.0", "plan.txt"},
                        "invalid --workers \"1.0\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers= 1", "plan.txt"},
                        "invalid --workers \" 1\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers=１２", "plan.txt"},
                        "invalid --workers \"１２\" (expected a whole number from 1 to 64)"),

                error(new String[]{"gantt", "--scale", "-1", "plan.txt"},
                        "invalid --scale \"-1\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"gantt", "--scale=0", "plan.txt"},
                        "invalid --scale \"0\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"gantt", "--scale=1001", "plan.txt"},
                        "invalid --scale \"1001\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"gantt", "--scale=99999999999", "plan.txt"},
                        "invalid --scale \"99999999999\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"gantt", "--scale=", "plan.txt"},
                        "invalid --scale \"\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"gantt", "--scale=01", "plan.txt"},
                        "invalid --scale \"01\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"gantt", "--scale=+1", "plan.txt"},
                        "invalid --scale \"+1\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"gantt", "--scale=1.0", "plan.txt"},
                        "invalid --scale \"1.0\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"gantt", "--scale= 1", "plan.txt"},
                        "invalid --scale \" 1\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"gantt", "--scale=١", "plan.txt"},
                        "invalid --scale \"١\" (expected a whole number from 1 to 1000)"),

                error(new String[]{"schedule", "--workers", "--scale=3", "plan.txt"},
                        "invalid --workers \"--scale=3\" (expected a whole number from 1 to 64)"),
                error(new String[]{"schedule", "--workers=0", "--scale=0", "plan.txt"},
                        "invalid --workers \"0\" (expected a whole number from 1 to 64)"),
                error(new String[]{"check", "--workers=1", "--scale=0", "plan.txt"},
                        "invalid --scale \"0\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"check", "--workers=0", "--scale=0"},
                        "invalid --workers \"0\" (expected a whole number from 1 to 64)"),
                error(new String[]{"check", "--workers=1", "--scale=0"},
                        "invalid --scale \"0\" (expected a whole number from 1 to 1000)"),
                error(new String[]{"check", "--workers=1", "--scale=1", "plan.txt"},
                        "--workers is only valid for schedule"),
                error(new String[]{"check", "--scale=1", "--workers=1", "plan.txt"},
                        "--workers is only valid for schedule"),
                error(new String[]{"schedule", "--workers=1", "--scale=1", "plan.txt"},
                        "--scale is only valid for gantt"),
                error(new String[]{"check", "--workers=1"},
                        "--workers is only valid for schedule"),
                error(new String[]{"gantt", "--scale=1"},
                        "missing FILE"),
                error(new String[]{"check"},
                        "missing FILE"),
                error(new String[]{"check", "one.txt", "two.txt"},
                        "too many arguments"),
                error(new String[]{"check", "--workers=1", "--scale=1"},
                        "--workers is only valid for schedule"),
                error(new String[]{"schedule", "one.txt", "--workers=2", "two.txt"},
                        "too many arguments"),
                error(new String[]{"schedule", "--workers=2", "one.txt", "--workers=3", "two.txt"},
                        "too many arguments")
        );
    }

    @Test
    void recognizesOnlyTheSingleArgumentHelpForm() {
        Options options = Options.parse(new String[]{"--help"});

        assertTrue(options.help());
        assertTrue(options.isHelp());
        assertEquals("", options.command());
        assertEquals(2, options.workers());
        assertEquals(1, options.scale());
    }

    @Test
    void exposesTheFourExactUsageLines() {
        assertEquals(String.join("\n",
                "usage: taskplan check FILE",
                "       taskplan critical [--max-slack N] FILE",
                "       taskplan schedule [--workers N] FILE",
                "       taskplan gantt [--scale K] FILE",
                "       taskplan order FILE"), Options.USAGE);
    }

    private static Arguments error(String[] args, String message) {
        return Arguments.of((Object) args, message);
    }

    private record ExpectedOptions(String[] args, String command, String file, int workers, int scale) {
    }
}
