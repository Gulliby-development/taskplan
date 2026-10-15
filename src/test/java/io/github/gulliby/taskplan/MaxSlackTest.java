package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MaxSlackTest {
    private static final String PLAN = """
            # release pipeline
            build: 5
            test: 8 after build
            lint: 2 after build
            package: 3 after test lint
            deploy: 1 after package
            docs: 4
            """;

    @TempDir
    Path tempDir;

    @Test
    void acceptsBothSpellingsAtDifferentArgumentPositionsAndInclusiveBoundaries() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);
        Invocation unfiltered = invoke("critical", plan.toString());
        Invocation zeroBeforeFile = invoke("critical", "--max-slack", "0", plan.toString());
        Invocation zeroAfterFile = invoke("critical", plan.toString(), "--max-slack=0");
        Invocation maximumBeforeFile = invoke("critical", "--max-slack=100000", plan.toString());
        Invocation maximumAfterFile = invoke("critical", plan.toString(), "--max-slack", "100000");

        assertEquals(0, zeroBeforeFile.status());
        assertEquals(zeroBeforeFile.stdout(), zeroAfterFile.stdout());
        assertEquals(unfiltered.stdout(), maximumBeforeFile.stdout());
        assertEquals(unfiltered.stdout(), maximumAfterFile.stdout());
        assertEquals("", zeroBeforeFile.stderr());
        assertEquals("", maximumBeforeFile.stderr());
    }

    @Test
    void theLastRepeatedValueTakesEffect() throws IOException {
        Path readmePlan = writePlan("plan.txt", PLAN);
        Invocation readmeRepeated = invoke(
                "critical", "--max-slack", "2", "--max-slack", "0", readmePlan.toString());
        Invocation readmeZero = invoke("critical", "--max-slack=0", readmePlan.toString());
        assertEquals(readmeZero.stdout(), readmeRepeated.stdout());

        Path discriminatingPlan = writePlan("repeated.txt", "a: 1\nb: 2\nc: 3 after a\n");
        Invocation zero = invoke("critical", "--max-slack=0", discriminatingPlan.toString());
        Invocation two = invoke("critical", "--max-slack=2", discriminatingPlan.toString());
        Invocation repeated = invoke(
                "critical", "--max-slack", "2", "--max-slack", "0", discriminatingPlan.toString());

        assertNotEquals(zero.stdout(), two.stdout());
        assertEquals(zero.stdout(), repeated.stdout());
        assertEquals(0, repeated.status());
        assertEquals("", repeated.stderr());
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void rejectsEverySpecifiedInvalidValueWithTheExactDiagnostic(String[] option, String value)
            throws IOException {
        Path plan = writePlan("plan.txt", PLAN);
        assertInvalidValue(invoke(append(option, plan.toString())), value);
        if (value.isEmpty()) {
            assertInvalidValue(invoke(append(new String[] {"--max-slack", ""}, plan.toString())), value);
        }
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of((Object) new String[] {"--max-slack", "-1"}, "-1"),
                Arguments.of((Object) new String[] {"--max-slack=+1"}, "+1"),
                Arguments.of((Object) new String[] {"--max-slack", "01"}, "01"),
                Arguments.of((Object) new String[] {"--max-slack=1.5"}, "1.5"),
                Arguments.of((Object) new String[] {"--max-slack", "x"}, "x"),
                Arguments.of((Object) new String[] {"--max-slack="}, ""),
                Arguments.of((Object) new String[] {"--max-slack=100001"}, "100001"));
    }

    @Test
    void aFinalMaxSlackOptionWithoutAValuePrecedesMissingFile() {
        Invocation result = invoke("critical", "--max-slack");

        assertEquals(2, result.status());
        assertEquals("", result.stdout());
        assertEquals("taskplan: --max-slack needs a value\n", result.stderr());
    }

    @ParameterizedTest
    @MethodSource("validationOrderCases")
    void preservesWorkersScaleAndCommandRestrictionPrecedence(String[] args, String message) throws IOException {
        Path plan = writePlan("plan.txt", PLAN);
        assertError(invoke(replaceFileMarker(args, plan)), message);
    }

    static Stream<Arguments> validationOrderCases() {
        return Stream.of(
                Arguments.of((Object) new String[] {
                        "critical", "--workers=0", "--max-slack=x", "FILE"
                }, "invalid --workers \"0\" (expected a whole number from 1 to 64)"),
                Arguments.of((Object) new String[] {
                        "critical", "--workers=0", "--scale=x", "--max-slack=x", "FILE"
                }, "invalid --workers \"0\" (expected a whole number from 1 to 64)"),
                Arguments.of((Object) new String[] {
                        "critical", "--scale=x", "--max-slack=x", "FILE"
                }, "invalid --scale \"x\" (expected a whole number from 1 to 1000)"),
                Arguments.of((Object) new String[] {
                        "critical", "--workers=1", "--scale=1", "--max-slack=1", "FILE"
                }, "--workers is only valid for schedule"),
                Arguments.of((Object) new String[] {
                        "critical", "--scale=1", "--max-slack=1", "FILE"
                }, "--scale is only valid for gantt"));
    }

    @Test
    void invalidMaxSlackValuesPrecedeCommandRestrictions() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);
        assertError(invoke("critical", "--workers=1", "--max-slack=x", plan.toString()),
                "invalid --max-slack \"x\" (expected a whole number from 0 to 100000)");
        assertError(invoke("schedule", "--max-slack=x", plan.toString()),
                "invalid --max-slack \"x\" (expected a whole number from 0 to 100000)");
    }

    @Test
    void restrictsOtherCommandsBeforeCheckingForMissingFile() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);
        assertError(invoke("schedule", "--max-slack=1", plan.toString()),
                "--max-slack is only valid for critical");
        assertError(invoke("gantt", "--max-slack", "0", plan.toString()),
                "--max-slack is only valid for critical");
        assertError(invoke("check", "--max-slack=0", plan.toString()),
                "--max-slack is only valid for critical");
        assertError(invoke("critical", "--max-slack=0"), "missing FILE");
        assertError(invoke("check", "--max-slack=0"), "--max-slack is only valid for critical");
    }

    @ParameterizedTest
    @MethodSource("filteredReports")
    void filtersRowsAndKeepsTheSummariesWithLfOutput(String[] option, String expected) throws IOException {
        Path plan = writePlan("plan.txt", PLAN);
        Invocation result = invoke(appendAfterCommand(option, plan.toString()));

        assertEquals(0, result.status());
        assertEquals(expected, result.stdout());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\r"));
        assertTrue(result.stdout().endsWith("\n"));
    }

    static Stream<Arguments> filteredReports() {
        String criticalOnly = output("""
                TASK     DUR  START  FINISH  SLACK
                build      5      0       5      0
                test       8      5      13      0
                package    3     13      16      0
                deploy     1     16      17      0
                length: 17
                critical tasks: build, test, package, deploy
                """);
        return Stream.of(
                Arguments.of((Object) new String[] {"--max-slack", "0"}, criticalOnly),
                Arguments.of((Object) new String[] {"--max-slack=5"}, criticalOnly),
                Arguments.of((Object) new String[] {"--max-slack", "6"}, output("""
                        TASK     DUR  START  FINISH  SLACK
                        build      5      0       5      0
                        test       8      5      13      0
                        lint       2      5       7      6
                        package    3     13      16      0
                        deploy     1     16      17      0
                        length: 17
                        critical tasks: build, test, package, deploy
                        """)),
                Arguments.of((Object) new String[] {"--max-slack=100000"}, output("""
                        TASK     DUR  START  FINISH  SLACK
                        build      5      0       5      0
                        test       8      5      13      0
                        lint       2      5       7      6
                        package    3     13      16      0
                        deploy     1     16      17      0
                        docs       4      0       4     13
                        length: 17
                        critical tasks: build, test, package, deploy
                        """)));
    }

    @Test
    void calculatesColumnWidthsFromTheHeaderAndRowsThatAreActuallyPrinted() throws IOException {
        Path plan = writePlan("narrow.txt", "a: 1\nverylongname: 1\nb: 5\n");
        Invocation unfiltered = invoke("critical", plan.toString());
        Invocation filtered = invoke("critical", "--max-slack=0", plan.toString());

        assertEquals(0, unfiltered.status());
        assertEquals(14, unfiltered.stdout().indexOf("DUR"));
        String[] rows = unfiltered.stdout().split("\n");
        assertEquals("a", rows[1].substring(0, 12).trim());
        assertEquals("verylongname", rows[2].substring(0, 12).trim());
        assertEquals("b", rows[3].substring(0, 12).trim());

        assertEquals(0, filtered.status());
        assertEquals(output("""
                TASK  DUR  START  FINISH  SLACK
                b       5      0       5      0
                length: 5
                critical tasks: b
                """), filtered.stdout());
    }

    @Test
    void plansWithDuplicatesUnknownDependenciesAndCyclesAreHandledAsBefore() throws IOException {
        Path plan = writePlan("problems.txt", """
                build: 5
                test: 8 after build bild
                build: 2
                a: 1 after c
                b: 1 after a
                c: 1 after b d
                d: 0 after d
                """);
        Invocation unfiltered = invoke("critical", plan.toString());
        Invocation filtered = invoke("critical", "--max-slack=0", plan.toString());

        assertEquals(1, unfiltered.status());
        assertEquals(1, filtered.status());
        assertEquals("", filtered.stdout());
        assertEquals(unfiltered.stderr(), filtered.stderr());
        assertTrue(filtered.stderr().contains("TP002 unknown dependency \"bild\""));
        assertTrue(filtered.stderr().contains("TP001 duplicate task \"build\""));
        assertTrue(filtered.stderr().contains("TP003 dependency cycle"));
    }

    private static void assertInvalidValue(Invocation result, String value) {
        assertEquals(2, result.status());
        assertEquals("", result.stdout());
        assertEquals("taskplan: invalid --max-slack \"" + value
                + "\" (expected a whole number from 0 to 100000)\n", result.stderr());
    }

    private static void assertError(Invocation result, String message) {
        assertEquals(2, result.status());
        assertEquals("", result.stdout());
        assertEquals("taskplan: " + message + "\n", result.stderr());
    }

    private Path writePlan(String name, String text) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private static Invocation invoke(String... args) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        try (PrintStream stdout = new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8);
                PrintStream stderr = new PrintStream(stderrBytes, true, StandardCharsets.UTF_8)) {
            int status = Cli.run(args, new ByteArrayInputStream(new byte[0]), stdout, stderr);
            return new Invocation(status, stdoutBytes.toString(StandardCharsets.UTF_8),
                    stderrBytes.toString(StandardCharsets.UTF_8));
        }
    }

    private static String[] append(String[] option, String file) {
        String[] args = new String[option.length + 2];
        args[0] = "critical";
        System.arraycopy(option, 0, args, 1, option.length);
        args[args.length - 1] = file;
        return args;
    }

    private static String[] appendAfterCommand(String[] option, String file) {
        String[] args = new String[option.length + 2];
        args[0] = "critical";
        System.arraycopy(option, 0, args, 1, option.length);
        args[args.length - 1] = file;
        return args;
    }

    private static String[] replaceFileMarker(String[] args, Path file) {
        return Arrays.stream(args)
                .map(argument -> argument.equals("FILE") ? file.toString() : argument)
                .toArray(String[]::new);
    }

    private static String output(String block) {
        return block.stripTrailing() + "\n";
    }

    private record Invocation(int status, String stdout, String stderr) {
    }
}
