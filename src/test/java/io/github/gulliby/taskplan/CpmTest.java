package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CpmTest {
    private static final String PARALLEL_PLAN = """
            root: 2
            left: 3 after root
            right: 3 after root
            leftEnd: 1 after left
            rightEnd: 1 after right
            side: 4
            """;

    @ParameterizedTest
    @CsvSource({
            "root,0,2,0,2,0",
            "left,2,5,2,5,0",
            "right,2,5,2,5,0",
            "leftEnd,5,6,5,6,0",
            "rightEnd,5,6,5,6,0",
            "side,0,4,2,6,2"
    })
    void computesEveryTimeAndSlackForParallelPaths(String name, long es, long ef, long ls, long lf, long slack) {
        Cpm cpm = cpm(PARALLEL_PLAN);

        assertEquals(es, cpm.earliestStart(name));
        assertEquals(ef, cpm.earliestFinish(name));
        assertEquals(ls, cpm.latestStart(name));
        assertEquals(lf, cpm.latestFinish(name));
        assertEquals(slack, cpm.slack(name));
        assertEquals(6, cpm.length());
    }

    @ParameterizedTest
    @CsvSource({
            "main,0,2,0,2,0",
            "milestone,2,2,2,2,0",
            "finish,2,5,2,5,0",
            "short,0,4,1,5,1"
    })
    void propagatesTimesThroughZeroDurationTasks(String name, long es, long ef, long ls, long lf, long slack) {
        Cpm cpm = cpm("""
                main: 2
                milestone: 0 after main
                finish: 3 after milestone
                short: 4
                """);

        assertEquals(es, cpm.earliestStart(name));
        assertEquals(ef, cpm.earliestFinish(name));
        assertEquals(ls, cpm.latestStart(name));
        assertEquals(lf, cpm.latestFinish(name));
        assertEquals(slack, cpm.slack(name));
        assertEquals(5, cpm.length());
    }

    @Test
    void ordersAllCriticalTasksByEarliestStartThenFileLineAcrossTiedChains() {
        Cpm cpm = cpm("""
                later: 2 after base
                base: 3
                other: 3
                tailA: 1 after later
                tailB: 3 after other
                """);

        assertEquals(6, cpm.length());
        assertEquals(List.of("base", "other", "later", "tailB", "tailA"),
                cpm.criticalTasks().stream().map(Task::name).toList());
    }

    @Test
    void formatsCriticalReportWithAlignedColumnsAndCriticalChainOrder() {
        Cpm cpm = cpm("""
                build: 5
                test: 8 after build
                lint: 2 after build
                package: 3 after test lint
                deploy: 1 after package
                docs: 4
                """);

        assertEquals(List.of(
                "TASK     DUR  START  FINISH  SLACK",
                "build      5      0       5      0",
                "test       8      5      13      0",
                "lint       2      5       7      6",
                "package    3     13      16      0",
                "deploy     1     16      17      0",
                "docs       4      0       4     13",
                "length: 17",
                "critical tasks: build, test, package, deploy"),
                Report.critical(cpm));
    }

    @Test
    void handlesAnAllMilestonePlanWithZeroProjectLength() {
        Cpm cpm = cpm("first: 0\nsecond: 0 after first\n");

        assertEquals(0, cpm.length());
        assertEquals(0, cpm.earliestStart("first"));
        assertEquals(0, cpm.earliestFinish("first"));
        assertEquals(0, cpm.latestStart("second"));
        assertEquals(0, cpm.latestFinish("second"));
        assertEquals(0, cpm.slack("first"));
        assertEquals(List.of("first", "second"),
                cpm.criticalTasks().stream().map(Task::name).toList());
    }

    @Test
    void exposesTimeMapsInFileOrderAndPreventsMutation() {
        Cpm cpm = cpm("second: 1\nfirst: 2\n");

        assertEquals(List.of("second", "first"), List.copyOf(cpm.earliestStarts().keySet()));
        assertEquals(Map.of("second", 0L, "first", 0L), cpm.earliestStarts());
        assertThrows(UnsupportedOperationException.class, () -> cpm.earliestStarts().put("new", 0L));
        assertThrows(UnsupportedOperationException.class, () -> cpm.criticalTasks().clear());
        assertThrows(UnsupportedOperationException.class, () -> cpm.tasks().clear());
    }

    @Test
    void rejectsQueriesForUnknownTasks() {
        Cpm cpm = cpm("known: 1\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cpm.earliestStart("missing"));
        assertEquals("unknown task: missing", exception.getMessage());
    }

    @Test
    void computesCriticalValuesForAChainOfTwentyThousandTasksIteratively() {
        int taskCount = 20_000;
        StringBuilder plan = new StringBuilder(taskCount * 28);
        for (int index = 0; index < taskCount; index++) {
            plan.append("task").append(index).append(": 1");
            if (index > 0) {
                plan.append(" after task").append(index - 1);
            }
            plan.append('\n');
        }

        Cpm cpm = cpm(plan.toString());

        assertEquals(taskCount, cpm.tasks().size());
        assertEquals(taskCount, cpm.length());
        assertEquals(taskCount - 1, cpm.earliestStart("task19999"));
        assertEquals(taskCount, cpm.earliestFinish("task19999"));
        assertEquals(taskCount - 1, cpm.latestStart("task19999"));
        assertEquals(taskCount, cpm.latestFinish("task19999"));
        assertEquals(0, cpm.slack("task0"));
        assertEquals(taskCount, cpm.criticalTasks().size());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0,0,0",
            "1,0,1,0,0",
            "2,0,2,0,0",
            "17,0,17,0,0"
    })
    void computesTimesForSingleTaskPlans(int duration, long es, long ef, long ls, long slack) {
        Cpm cpm = cpm("only: " + duration + "\n");

        assertEquals(es, cpm.earliestStart("only"));
        assertEquals(ef, cpm.earliestFinish("only"));
        assertEquals(ls, cpm.latestStart("only"));
        assertEquals(ef, cpm.latestFinish("only"));
        assertEquals(slack, cpm.slack("only"));
        assertEquals(duration, cpm.length());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0",
            "0,1",
            "1,0",
            "1,1",
            "0,100000",
            "100000,0",
            "100000,100000",
            "99999,1",
            "1,99999",
            "50000,50000",
            "2,3",
            "3,2",
            "4,6",
            "6,4",
            "10,0",
            "0,10",
            "17,23",
            "23,17",
            "99,101",
            "100,100",
            "999,1000"
    })
    void preservesBoundaryAndUnequalDurationsAcrossAChain(int firstDuration, int secondDuration) {
        Cpm cpm = cpm("first: " + firstDuration + "\nsecond: " + secondDuration + " after first\n");
        long finish = (long) firstDuration + secondDuration;

        assertEquals(0, cpm.earliestStart("first"));
        assertEquals(firstDuration, cpm.earliestFinish("first"));
        assertEquals(0, cpm.latestStart("first"));
        assertEquals(0, cpm.slack("first"));
        assertEquals(firstDuration, cpm.earliestStart("second"));
        assertEquals(finish, cpm.earliestFinish("second"));
        assertEquals(firstDuration, cpm.latestStart("second"));
        assertEquals(finish, cpm.latestFinish("second"));
        assertEquals(0, cpm.slack("second"));
        assertEquals(finish, cpm.length());
    }

    @Test
    void rejectsConstructionWhenTheGraphHasBlockingProblems() {
        Graph graph = new Graph(PlanParser.parse("a: 1 after missing\n", "plan"));

        assertTrue(graph.hasBlockingProblems());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new Cpm(graph));
        assertEquals("graph has blocking problems", exception.getMessage());
    }

    private static Cpm cpm(String plan) {
        return new Cpm(new Graph(PlanParser.parse(plan, "plan")));
    }
}
