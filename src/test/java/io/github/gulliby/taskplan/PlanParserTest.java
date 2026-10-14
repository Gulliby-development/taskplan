package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PlanParserTest {
    @Test
    void parsesTasksInFileOrderAndDeduplicatesDependenciesWithoutReordering() {
        List<Task> tasks = PlanParser.parse(
                "build: 5\npackage: 3 after test  build test\ntest: 8 after build",
                "plan.txt");

        assertEquals(List.of(
                new Task("build", 5, List.of(), 1),
                new Task("package", 3, List.of("test", "build"), 2),
                new Task("test", 8, List.of("build"), 3)), tasks);
        assertEquals(List.of("test", "build"), tasks.get(1).dependencies());
    }

    @ParameterizedTest
    @MethodSource("validTaskLines")
    void acceptsValidNameAndDurationBoundaries(String line, String name, int duration) {
        List<Task> tasks = PlanParser.parse(line, "plan.txt");

        assertEquals(1, tasks.size());
        assertEquals(name, tasks.get(0).name());
        assertEquals(duration, tasks.get(0).duration());
        assertEquals(1, tasks.get(0).line());
    }

    static Stream<Arguments> validTaskLines() {
        return Stream.of(
                Arguments.of("A: 0", "A", 0),
                Arguments.of("A: 000000", "A", 0),
                Arguments.of("task-name_1.x: 000001", "task-name_1.x", 1),
                Arguments.of("Task: 100000", "Task", 100000),
                Arguments.of("A".repeat(40) + ": 7", "A".repeat(40), 7),
                Arguments.of("x: 1 after A-1 _b .c Z9", "x", 1));
    }

    @ParameterizedTest
    @MethodSource("validWhitespacePlans")
    void skipsBlankAndCommentLinesAndCountsPhysicalLines(String text, List<Integer> expectedLines) {
        List<Task> tasks = PlanParser.parse(text, "plan.txt");

        assertEquals(expectedLines, tasks.stream().map(Task::line).toList());
        assertEquals(List.of("first", "second"), tasks.stream().map(Task::name).toList());
    }

    static Stream<Arguments> validWhitespacePlans() {
        return Stream.of(
                Arguments.of("first: 1\n\n# comment\nsecond: 2", List.of(1, 4)),
                Arguments.of("\t# comment\t\n first: 1 \n \t\n\tsecond: 2\t", List.of(2, 4)),
                Arguments.of("# first\n\t\nfirst: 1\n# between\nsecond: 2\n", List.of(3, 5)));
    }

    @Test
    void acceptsLeadingBomAndCrLfLineEndings() {
        List<Task> tasks = PlanParser.parse("\uFEFF# plan\r\nx: 3\r\ny: 0 after x\r\n", "bom.txt");

        assertEquals(List.of(
                new Task("x", 3, List.of(), 2),
                new Task("y", 0, List.of("x"), 3)), tasks);
    }

    @ParameterizedTest
    @MethodSource("invalidLines")
    void reportsEachSpecifiedLineErrorExactly(String line, String message) {
        PlanException exception = assertThrows(
                PlanException.class, () -> PlanParser.parse(line, "input.plan"));

        assertEquals("input.plan:1: " + message, exception.getMessage());
    }

    static Stream<Arguments> invalidLines() {
        return Stream.of(
                Arguments.of("x", "expected \"NAME: DURATION [after NAME...]\""),
                Arguments.of("bad name: 1", "invalid task name \"bad name\""),
                Arguments.of("bad/name: 1", "invalid task name \"bad/name\""),
                Arguments.of("x:", "missing duration"),
                Arguments.of("x: \t", "missing duration"),
                Arguments.of("x: -1", "invalid duration \"-1\" (expected a whole number from 0 to 100000)"),
                Arguments.of("x: +1", "invalid duration \"+1\" (expected a whole number from 0 to 100000)"),
                Arguments.of("x: 1.0", "invalid duration \"1.0\" (expected a whole number from 0 to 100000)"),
                Arguments.of("x: 100001", "invalid duration \"100001\" (expected a whole number from 0 to 100000)"),
                Arguments.of("x: 1000000", "invalid duration \"1000000\" (expected a whole number from 0 to 100000)"),
                Arguments.of("x: duration", "invalid duration \"duration\" (expected a whole number from 0 to 100000)"),
                Arguments.of("x: 1 later y", "expected \"after\" or end of line, found \"later\""),
                Arguments.of("x: 1 AFTER y", "expected \"after\" or end of line, found \"AFTER\""),
                Arguments.of("x: 1 after", "\"after\" needs at least one task name"),
                Arguments.of("x: 1 after \t", "\"after\" needs at least one task name"),
                Arguments.of("x: 1 after bad/name", "invalid dependency name \"bad/name\""),
                Arguments.of("x: 1 after " + "a".repeat(41), "invalid dependency name \"" + "a".repeat(41) + "\""));
    }

    @ParameterizedTest
    @MethodSource("unprintableCharacters")
    void rendersEachNonAsciiCodePointAsOneQuestionMark(String input, String message) {
        PlanException exception = assertThrows(
                PlanException.class, () -> PlanParser.parse(input, "unicode.plan"));

        assertEquals("unicode.plan:1: " + message, exception.getMessage());
    }

    static Stream<Arguments> unprintableCharacters() {
        return Stream.of(
                Arguments.of("na\u00EFve: 1", "invalid task name \"na?ve\""),
                Arguments.of("x: 1 after snow\u2603", "invalid dependency name \"snow?\""),
                Arguments.of("x: 1 \u00A0", "expected \"after\" or end of line, found \"?\""),
                Arguments.of("x: \uD83D\uDE00", "invalid duration \"?\" (expected a whole number from 0 to 100000)"));
    }

    @ParameterizedTest
    @MethodSource("emptyPlans")
    void rejectsPlansWithNoTasks(String text) {
        PlanException exception = assertThrows(
                PlanException.class, () -> PlanParser.parse(text, "empty.txt"));

        assertEquals("empty.txt: no tasks", exception.getMessage());
    }

    static Stream<Arguments> emptyPlans() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of(" \t\n\t\n"),
                Arguments.of("# comment\n  # another comment\t\n"));
    }

    @Test
    void reportsOnlyTheFirstInvalidLineWithItsPhysicalLineNumber() {
        PlanException exception = assertThrows(
                PlanException.class,
                () -> PlanParser.parse("# header\nvalid: 1\ninvalid\nother: -1", "first-error.txt"));

        assertEquals("first-error.txt:3: expected \"NAME: DURATION [after NAME...]\"", exception.getMessage());
    }

    @Test
    void returnedTaskListAndDependencyListsAreImmutable() {
        List<Task> tasks = PlanParser.parse("x: 1 after a b", "plan.txt");

        assertThrows(UnsupportedOperationException.class, () -> tasks.add(new Task("y", 1, List.of(), 2)));
        assertThrows(UnsupportedOperationException.class, () -> tasks.get(0).dependencies().add("c"));
        assertTrue(tasks.get(0).dependencies().contains("a"));
    }
}
