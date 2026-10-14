package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GanttTest {
    @Test
    void rendersEarliestStartRowsWithTaskSlackAndUnusedCells() {
        String plan = """
                main: 4
                tail: 2 after main
                float: 1
                """;

        assertEquals(List.of(
                "main  |####..|",
                "float |#-----|",
                "tail  |....##|"),
                render(plan, 1));
    }

    @Test
    void scaleCombinesIntervalsIntoCellsAndRoundsCellCountUp() {
        String plan = """
                main: 4
                tail: 2 after main
                float: 1
                """;

        assertEquals(List.of(
                "main  |##.|",
                "float |#--|",
                "tail  |..#|"),
                render(plan, 2));
    }

    @Test
    void ordersTiedStartsByFileLineAndPadsNamesToTheWidestTask() {
        String plan = """
                later: 1 after base
                zero: 0
                base: 1
                """;

        assertEquals(List.of(
                "zero  |*.|",
                "base  |#.|",
                "later |.#|"),
                render(plan, 1));
    }

    @Test
    void placesEndMilestoneInTheLastCellWhenItsStartIsPastTheChart() {
        String plan = """
                work: 2
                milestone: 0 after work
                """;

        assertEquals(List.of(
                "work      |##|",
                "milestone |.*|"),
                render(plan, 1));
    }

    @Test
    void aZeroLengthPlanStillHasOneMilestoneCell() {
        assertEquals(List.of("instant |*|"), render("instant: 0\n", 1));
    }

    @Test
    void maximumScaleIsAccepted() {
        assertEquals(List.of("work |#|"), render("work: 1000\n", 1000));
    }

    @Test
    void allowsExactlyTwoHundredCellsAndRejectsTheNextCell() {
        assertEquals(List.of("work |" + "#".repeat(200) + "|"),
                render("work: 200\n", 1));

        PlanException exception = assertThrows(PlanException.class,
                () -> render("work: 201\n", 1));
        assertEquals("chart too wide (201 cells, limit 200; use --scale)", exception.getMessage());

        assertEquals(List.of("work |" + "#".repeat(101) + "|"),
                render("work: 201\n", 2));
    }

    @ParameterizedTest
    @CsvSource({"0", "1001"})
    void rejectsScaleOutsideTheSupportedRange(int scale) {
        Cpm cpm = cpm("work: 1\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Gantt.render(cpm, scale));
        assertEquals("scale must be between 1 and 1000", exception.getMessage());
    }

    private static List<String> render(String plan, int scale) {
        return Gantt.render(cpm(plan), scale);
    }

    private static Cpm cpm(String plan) {
        return new Cpm(new Graph(PlanParser.parse(plan, "plan")));
    }
}
