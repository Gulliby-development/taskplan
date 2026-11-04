package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrderReverseTest {
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
    void reverseListsTheNormalOrderBackwards() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        Invocation result = invoke("order", "--reverse", plan.toString());

        assertEquals(0, result.status());
        assertEquals("docs\ndeploy\npackage\nlint\ntest\nbuild\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void reverseMayComeAfterTheFile() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        Invocation result = invoke("order", plan.toString(), "--reverse");

        assertEquals(0, result.status());
        assertEquals("docs\ndeploy\npackage\nlint\ntest\nbuild\n", result.stdout());
    }

    @Test
    void aRepeatedReverseIsTheSameAsOne() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        Invocation once = invoke("order", "--reverse", plan.toString());
        Invocation twice = invoke("order", "--reverse", "--reverse", plan.toString());

        assertEquals(once.stdout(), twice.stdout());
        assertEquals(0, twice.status());
    }

    @Test
    void reverseOfOneTaskIsThatTask() throws IOException {
        Path plan = writePlan("one.txt", "only: 3\n");

        Invocation result = invoke("order", "--reverse", plan.toString());

        assertEquals(0, result.status());
        assertEquals("only\n", result.stdout());
    }

    @Test
    void reverseReadsStandardInputToo() {
        Invocation result = invoke(PLAN.getBytes(StandardCharsets.UTF_8), "order", "--reverse", "-");

        assertEquals(0, result.status());
        assertEquals("docs\ndeploy\npackage\nlint\ntest\nbuild\n", result.stdout());
    }

    @Test
    void withoutReverseTheOrderIsUnchanged() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        Invocation result = invoke("order", plan.toString());

        assertEquals("build\ntest\nlint\npackage\ndeploy\ndocs\n", result.stdout());
    }

    @Test
    void reverseStopsOnTheSamePlanProblemsAsOrder() throws IOException {
        Path plan = writePlan("cycle.txt", "a: 1 after b\nb: 1 after a\n");

        Invocation plain = invoke("order", plan.toString());
        Invocation reversed = invoke("order", "--reverse", plan.toString());

        assertEquals(1, reversed.status());
        assertEquals(plain.status(), reversed.status());
        assertEquals(plain.stderr(), reversed.stderr());
        assertEquals("", reversed.stdout());
    }

    @Test
    void reverseIsOnlyValidForOrder() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        for (String command : new String[] {"check", "critical", "schedule", "gantt"}) {
            Invocation result = invoke(command, "--reverse", plan.toString());

            assertEquals(2, result.status(), command);
            assertEquals("", result.stdout(), command);
            assertEquals("taskplan: --reverse is only valid for order\n", result.stderr(), command);
        }
    }

    @Test
    void reverseTakesNoValue() throws IOException {
        Path plan = writePlan("plan.txt", PLAN);

        Invocation result = invoke("order", "--reverse=yes", plan.toString());

        assertEquals(2, result.status());
        assertEquals("taskplan: unknown option \"--reverse=yes\"\n", result.stderr());
    }

    @Test
    void reverseWithoutAFileIsMissingFile() {
        Invocation result = invoke("order", "--reverse");

        assertEquals(2, result.status());
        assertEquals("taskplan: missing FILE\n", result.stderr());
    }

    @Test
    void parseSetsTheReverseFlag() {
        assertEquals(true, Options.parse(new String[] {"order", "--reverse", "plan.txt"}).reverse());
        assertEquals(false, Options.parse(new String[] {"order", "plan.txt"}).reverse());
    }

    private Path writePlan(String name, String text) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private static Invocation invoke(String... args) {
        return invoke(new byte[0], args);
    }

    private static Invocation invoke(byte[] stdin, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = Cli.run(args, new ByteArrayInputStream(stdin), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Invocation(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Invocation(int status, String stdout, String stderr) {
    }
}
