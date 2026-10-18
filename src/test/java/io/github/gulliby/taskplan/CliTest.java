package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CliTest {
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
    void helpPrintsExactlyFourUsageLinesAndDoesNotWriteToStderr() {
        Invocation result = invoke(new byte[0], "--help");

        assertEquals(0, result.status());
        assertEquals("""
                usage: taskplan check FILE
                       taskplan critical [--max-slack N] FILE
                       taskplan schedule [--workers N] FILE
                       taskplan gantt [--scale K] FILE
                       taskplan order FILE
                """, result.stdout());
        assertEquals("", result.stderr());
    }

    @ParameterizedTest
    @MethodSource("argumentErrors")
    void reportsArgumentErrorsWithExactMessageAndNoStdout(String[] args, String message) {
        Invocation result = invoke(new byte[0], args);

        assertEquals(2, result.status());
        assertEquals("", result.stdout());
        assertEquals("taskplan: " + message + "\n", result.stderr());
    }

    static Stream<Arguments> argumentErrors() {
        return Stream.of(
                Arguments.of((Object) new String[0],
                        "missing command (check, critical, schedule, gantt or order)"),
                Arguments.of((Object) new String[] {"unknown"},
                        "unknown command \"unknown\""),
                Arguments.of((Object) new String[] {"--help", "FILE"},
                        "unknown option \"--help\""),
                Arguments.of((Object) new String[] {"check", "--help", "FILE"},
                        "unknown option \"--help\""),
                Arguments.of((Object) new String[] {"check", "--unknown", "FILE"},
                        "unknown option \"--unknown\""),
                Arguments.of((Object) new String[] {"schedule", "--workers"},
                        "--workers needs a value"),
                Arguments.of((Object) new String[] {"gantt", "--scale"},
                        "--scale needs a value"),
                Arguments.of((Object) new String[] {"schedule", "--workers", "-1", "FILE"},
                        "invalid --workers \"-1\" (expected a whole number from 1 to 64)"),
                Arguments.of((Object) new String[] {"schedule", "--workers=0", "FILE"},
                        "invalid --workers \"0\" (expected a whole number from 1 to 64)"),
                Arguments.of((Object) new String[] {"schedule", "--workers=65", "FILE"},
                        "invalid --workers \"65\" (expected a whole number from 1 to 64)"),
                Arguments.of((Object) new String[] {"schedule", "--workers=01", "FILE"},
                        "invalid --workers \"01\" (expected a whole number from 1 to 64)"),
                Arguments.of((Object) new String[] {"schedule", "--workers=+1", "FILE"},
                        "invalid --workers \"+1\" (expected a whole number from 1 to 64)"),
                Arguments.of((Object) new String[] {"schedule", "--workers=99999999999", "FILE"},
                        "invalid --workers \"99999999999\" (expected a whole number from 1 to 64)"),
                Arguments.of((Object) new String[] {"gantt", "--scale=0", "FILE"},
                        "invalid --scale \"0\" (expected a whole number from 1 to 1000)"),
                Arguments.of((Object) new String[] {"gantt", "--scale=1001", "FILE"},
                        "invalid --scale \"1001\" (expected a whole number from 1 to 1000)"),
                Arguments.of((Object) new String[] {"gantt", "--scale=01", "FILE"},
                        "invalid --scale \"01\" (expected a whole number from 1 to 1000)"),
                Arguments.of((Object) new String[] {"gantt", "--scale=99999999999", "FILE"},
                        "invalid --scale \"99999999999\" (expected a whole number from 1 to 1000)"),
                Arguments.of((Object) new String[] {"critical", "--workers=0", "--scale=0", "FILE"},
                        "invalid --workers \"0\" (expected a whole number from 1 to 64)"),
                Arguments.of((Object) new String[] {"critical", "--scale=0", "FILE"},
                        "invalid --scale \"0\" (expected a whole number from 1 to 1000)"),
                Arguments.of((Object) new String[] {"critical", "--workers=1", "FILE"},
                        "--workers is only valid for schedule"),
                Arguments.of((Object) new String[] {"critical", "--scale=1", "FILE"},
                        "--scale is only valid for gantt"),
                Arguments.of((Object) new String[] {"schedule"},
                        "missing FILE"),
                Arguments.of((Object) new String[] {"schedule", "--workers=2"},
                        "missing FILE"),
                Arguments.of((Object) new String[] {"check", "one.plan", "two.plan"},
                        "too many arguments"),
                Arguments.of((Object) new String[] {"schedule", "--workers=0", "--unknown"},
                        "unknown option \"--unknown\""));
    }

    @Test
    void checkPrintsSingularTaskCountAndAcceptsDashAsStandardInput() {
        Invocation result = invoke(bytes("\uFEFF# plan\r\nsolo: 0\r\n"), "check", "-");

        assertEquals(0, result.status());
        assertEquals("<stdin>: ok, 1 task\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void checkPrintsPluralTaskCountForAFile() throws IOException {
        Path plan = writePlan("check.txt", "first: 1\nsecond: 0 after first\n");

        Invocation result = invoke(new byte[0], "check", plan.toString());

        assertEquals(0, result.status());
        assertEquals(display(plan) + ": ok, 2 tasks\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void criticalPrintsTheSpecifiedExampleExactly() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        Invocation result = invoke(new byte[0], "critical", plan.toString());

        assertEquals(0, result.status());
        assertEquals(output("""
                TASK     DUR  START  FINISH  SLACK
                build      5      0       5      0
                test       8      5      13      0
                lint       2      5       7      6
                package    3     13      16      0
                deploy     1     16      17      0
                docs       4      0       4     13
                length: 17
                critical tasks: build, test, package, deploy
                """), result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void schedulePrintsTheSpecifiedDefaultWorkerExampleExactly() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        Invocation result = invoke(new byte[0], "schedule", plan.toString());

        assertEquals(0, result.status());
        assertEquals(output("""
                TASK     WORKER  START  FINISH
                build         1      0       5
                docs          2      0       4
                test          1      5      13
                lint          2      5       7
                package       1     13      16
                deploy        1     16      17
                makespan: 17
                workers: 2
                utilization: 68%
                """), result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void scheduleHonorsWorkerBoundariesAndLastOptionValue() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        Invocation oneWorker = invoke(new byte[0], "schedule", "--workers", "1", plan.toString());
        Invocation lastValueWins = invoke(
                new byte[0], "schedule", "--workers=1", "--workers", "3", plan.toString());

        assertEquals(0, oneWorker.status());
        assertTrue(oneWorker.stdout().endsWith(
                "makespan: 23\nworkers: 1\nutilization: 100%\n"));
        assertEquals("", oneWorker.stderr());

        assertEquals(0, lastValueWins.status());
        assertTrue(lastValueWins.stdout().endsWith(
                "makespan: 17\nworkers: 3\nutilization: 45%\n"));
        assertEquals("", lastValueWins.stderr());
    }

    @Test
    void scheduleAcceptsTheMaximumWorkerCount() throws IOException {
        Path plan = writePlan("single.txt", "only: 1\n");

        Invocation result = invoke(new byte[0], "schedule", "--workers=64", plan.toString());

        assertEquals(0, result.status());
        assertTrue(result.stdout().endsWith("makespan: 1\nworkers: 64\nutilization: 2%\n"));
        assertEquals("", result.stderr());
    }

    @Test
    void ganttPrintsDefaultAndScaledExamplesExactly() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        Invocation defaultScale = invoke(new byte[0], "gantt", plan.toString());
        Invocation scaleTwo = invoke(new byte[0], "gantt", "--scale=2", plan.toString());

        assertEquals(0, defaultScale.status());
        assertEquals(output("""
                build   |#####............|
                docs    |####-------------|
                test    |.....########....|
                lint    |.....##------....|
                package |.............###.|
                deploy  |................#|
                """), defaultScale.stdout());
        assertEquals("", defaultScale.stderr());

        assertEquals(0, scaleTwo.status());
        assertEquals(output("""
                build   |###......|
                docs    |##-------|
                test    |..#####..|
                lint    |..##---..|
                package |......##.|
                deploy  |........#|
                """), scaleTwo.stdout());
        assertEquals("", scaleTwo.stderr());
    }

    @Test
    void ganttAcceptsMaximumScale() throws IOException {
        Path plan = writePlan("one.plan", "x: 1\n");

        Invocation result = invoke(new byte[0], "gantt", "--scale", "1000", plan.toString());

        assertEquals(0, result.status());
        assertEquals("x |#|\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void checkPrintsStructuralProblemsAndOtherCommandsRejectThemOnStderr() throws IOException {
        String badPlan = """
                build: 5
                test: 8 after build bild
                build: 2
                a: 1 after c
                b: 1 after a
                c: 1 after b d
                d: 0 after d
                """;
        Path plan = writePlan("bad.txt", badPlan);
        String diagnostics = display(plan) + ":2: TP002 unknown dependency \"bild\" of task \"test\"\n"
                + display(plan) + ":3: TP001 duplicate task \"build\" (first on line 1)\n"
                + display(plan) + ":4: TP003 dependency cycle among \"a\", \"b\", \"c\"\n"
                + display(plan) + ":7: TP003 dependency cycle among \"d\"\n";

        Invocation check = invoke(new byte[0], "check", plan.toString());
        Invocation schedule = invoke(new byte[0], "schedule", plan.toString());

        assertEquals(1, check.status());
        assertEquals(diagnostics, check.stdout());
        assertEquals("", check.stderr());

        assertEquals(1, schedule.status());
        assertEquals("", schedule.stdout());
        String prefixedDiagnostics = diagnostics.lines()
                .map(line -> "taskplan: " + line)
                .collect(Collectors.joining("\n", "", "\n"));
        assertEquals(prefixedDiagnostics, schedule.stderr());
    }

    @Test
    void scheduleIgnoresRedundantDependencyWarnings() throws IOException {
        Path plan = writePlan("redundant.plan", """
                a: 2
                b: 3 after a
                c: 1 after a b
                d: 1 after c a b
                """);

        Invocation result = invoke(new byte[0], "schedule", plan.toString());

        assertEquals(0, result.status());
        assertTrue(result.stdout().startsWith("TASK"));
        assertTrue(result.stdout().endsWith("makespan: 7\nworkers: 2\nutilization: 50%\n"));
        assertEquals("", result.stderr());
    }

    @Test
    void reportsMissingFileWithSlashNormalizedPath() {
        Path missing = tempDir.resolve("not-there.plan");

        Invocation result = invoke(new byte[0], "check", missing.toString());

        assertEquals(2, result.status());
        assertEquals("", result.stdout());
        assertEquals("taskplan: " + display(missing) + ": cannot read the file\n", result.stderr());
    }

    @Test
    void reportsInvalidUtf8ForFileAndStandardInput() throws IOException {
        byte[] invalidUtf8 = {(byte) 0xC3, (byte) 0x28};
        Path plan = tempDir.resolve("invalid-utf8.plan");
        Files.write(plan, invalidUtf8);

        Invocation fileResult = invoke(new byte[0], "check", plan.toString());
        Invocation stdinResult = invoke(invalidUtf8, "check", "-");

        assertEquals(2, fileResult.status());
        assertEquals("", fileResult.stdout());
        assertEquals("taskplan: " + display(plan) + ": input is not valid UTF-8\n", fileResult.stderr());

        assertEquals(2, stdinResult.status());
        assertEquals("", stdinResult.stdout());
        assertEquals("taskplan: <stdin>: input is not valid UTF-8\n", stdinResult.stderr());
    }

    @Test
    void reportsFirstParserErrorAndEmptyPlanAsInputErrors() throws IOException {
        Path malformed = writePlan("malformed.plan", "# heading\nvalid: 1\ninvalid\nother: -1\n");
        Path empty = writePlan("empty.plan", " # nothing here\n\t\n");

        Invocation malformedResult = invoke(new byte[0], "check", malformed.toString());
        Invocation emptyResult = invoke(new byte[0], "critical", empty.toString());

        assertEquals(2, malformedResult.status());
        assertEquals("", malformedResult.stdout());
        assertEquals("taskplan: " + display(malformed)
                + ":3: expected \"NAME: DURATION [after NAME...]\"\n", malformedResult.stderr());

        assertEquals(2, emptyResult.status());
        assertEquals("", emptyResult.stdout());
        assertEquals("taskplan: " + display(empty) + ": no tasks\n", emptyResult.stderr());
    }

    @Test
    void ganttRejectsMoreThanTwoHundredCells() throws IOException {
        Path plan = writePlan("wide.plan", "long: 201\n");

        Invocation result = invoke(new byte[0], "gantt", plan.toString());

        assertEquals(2, result.status());
        assertEquals("", result.stdout());
        assertEquals("taskplan: chart too wide (201 cells, limit 200; use --scale)\n", result.stderr());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void checkAndCriticalHandleTwentyThousandTaskChainWithoutRecursion() {
        int taskCount = 20_000;
        byte[] input = bytes(chainPlan(taskCount));
        Invocation check = invoke(input, "check", "-");
        Invocation critical = invoke(input, "critical", "-");

        assertEquals(0, check.status());
        assertEquals("<stdin>: ok, 20000 tasks\n", check.stdout());
        assertEquals("", check.stderr());

        assertEquals(0, critical.status());
        assertEquals("", critical.stderr());
        String[] lines = critical.stdout().split("\n");
        assertEquals(taskCount + 3, lines.length);
        assertEquals("length: 20000", lines[taskCount + 1]);
        assertTrue(lines[taskCount + 2].startsWith("critical tasks: task0, task1,"));
        assertTrue(lines[taskCount + 2].endsWith("task19999"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void scheduleHandlesThreeThousandTaskChainWithoutRecursion() {
        int taskCount = 3_000;
        Invocation result = invoke(bytes(chainPlan(taskCount)), "schedule", "--workers=2", "-");

        assertEquals(0, result.status());
        assertEquals("", result.stderr());
        String[] lines = result.stdout().split("\n");
        assertEquals(taskCount + 4, lines.length);
        assertEquals("makespan: 3000", lines[taskCount + 1]);
        assertEquals("workers: 2", lines[taskCount + 2]);
        assertEquals("utilization: 50%", lines[taskCount + 3]);
    }

    private Path writePlan(String fileName, String text) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private static Invocation invoke(byte[] stdin, String... args) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        try (PrintStream stdout = new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8);
                PrintStream stderr = new PrintStream(stderrBytes, true, StandardCharsets.UTF_8)) {
            int status = Cli.run(args, new ByteArrayInputStream(stdin), stdout, stderr);
            return new Invocation(status, stdoutBytes.toString(StandardCharsets.UTF_8),
                    stderrBytes.toString(StandardCharsets.UTF_8));
        }
    }

    private static String display(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static byte[] bytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    private static String chainPlan(int taskCount) {
        StringBuilder plan = new StringBuilder(taskCount * 20);
        for (int index = 0; index < taskCount; index++) {
            plan.append("task").append(index).append(": 1");
            if (index > 0) {
                plan.append(" after task").append(index - 1);
            }
            plan.append('\n');
        }
        return plan.toString();
    }

    private static String output(String block) {
        return block.stripTrailing() + "\n";
    }

    private record Invocation(int status, String stdout, String stderr) {
    }
}
