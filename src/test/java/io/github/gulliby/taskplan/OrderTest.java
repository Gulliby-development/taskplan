package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OrderTest {
    private static final String README_PLAN = """
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
    void orderPrintsTheReadmeExampleExactly() throws IOException {
        Path plan = writePlan("plan.txt", README_PLAN);

        Invocation result = invoke(new byte[0], "order", plan.toString());

        assertEquals(0, result.status());
        assertEquals("build\ntest\nlint\npackage\ndeploy\ndocs\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void orderUsesFileOrderAmongReadyTasks() throws IOException {
        Path plan = writePlan("ready.plan", """
                c: 1 after a b
                b: 2
                a: 1
                d: 0 after c
                e: 3
                """);

        Invocation result = invoke(new byte[0], "order", plan.toString());

        assertEquals(0, result.status());
        assertEquals("b\na\nc\nd\ne\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void orderReadsStandardInputWhenFileIsDash() {
        Invocation result = invoke(bytes("first: 1\nsecond: 0 after first\n"), "order", "-");

        assertEquals(0, result.status());
        assertEquals("first\nsecond\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void orderIgnoresRedundantDependencies() throws IOException {
        Path plan = writePlan("redundant.plan", """
                a: 1
                b: 2 after a
                c: 3 after a b
                """);

        Invocation result = invoke(new byte[0], "order", plan.toString());

        assertEquals(0, result.status());
        assertEquals("a\nb\nc\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void orderPrintsAPlanWithOneTask() throws IOException {
        Path plan = writePlan("single.plan", "only: 0\n");

        Invocation result = invoke(new byte[0], "order", plan.toString());

        assertEquals(0, result.status());
        assertEquals("only\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void orderReportsCyclesOnStderrWithoutPrintingTasks() throws IOException {
        Path plan = writePlan("cycle.plan", "a: 1 after b\nb: 1 after a\n");

        Invocation result = invoke(new byte[0], "order", plan.toString());

        assertEquals(1, result.status());
        assertEquals("", result.stdout());
        assertEquals("taskplan: " + display(plan) + ":1: TP003 dependency cycle among \"a\", \"b\"\n",
                result.stderr());
    }

    @Test
    void orderReportsUnknownDependenciesOnStderrWithoutPrintingTasks() throws IOException {
        Path plan = writePlan("unknown.plan", "a: 1 after missing\n");

        Invocation result = invoke(new byte[0], "order", plan.toString());

        assertEquals(1, result.status());
        assertEquals("", result.stdout());
        assertEquals("taskplan: " + display(plan)
                + ":1: TP002 unknown dependency \"missing\" of task \"a\"\n", result.stderr());
    }

    @ParameterizedTest
    @MethodSource("argumentErrors")
    void orderReportsArgumentErrorsExactly(String[] args, String message) {
        Invocation result = invoke(new byte[0], args);

        assertEquals(2, result.status());
        assertEquals("", result.stdout());
        assertEquals("taskplan: " + message + "\n", result.stderr());
    }

    static Stream<Arguments> argumentErrors() {
        return Stream.of(
                Arguments.of((Object) new String[] {"order", "--scale", "2", "plan.txt"},
                        "--scale is only valid for gantt"),
                Arguments.of((Object) new String[] {"order", "--workers", "2", "plan.txt"},
                        "--workers is only valid for schedule"),
                Arguments.of((Object) new String[] {"order", "--max-slack", "1", "plan.txt"},
                        "--max-slack is only valid for critical"),
                Arguments.of((Object) new String[] {"order", "--bogus", "plan.txt"},
                        "unknown option \"--bogus\""),
                Arguments.of((Object) new String[] {"order"}, "missing FILE"),
                Arguments.of((Object) new String[] {"order", "a", "b"}, "too many arguments"));
    }

    private Path writePlan(String fileName, String text) throws IOException {
        Path path = tempDir.toRealPath().resolve(fileName);
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
        return path.toString().replace((char) 92, '/');
    }

    private static byte[] bytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    private record Invocation(int status, String stdout, String stderr) {
    }
}
