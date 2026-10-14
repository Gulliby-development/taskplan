package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SchedulerTest {
    private record ExpectedAssignment(String task, String worker, long start, long finish) {
    }

    @ParameterizedTest
    @CsvSource({
            "1, 14, 100",
            "2, 7, 100",
            "3, 5, 93",
            "4, 5, 70",
            "64, 5, 4"
    })
    void schedulesParallelTasksWithDifferentWorkerCounts(
            int workers, long expectedMakespan, long expectedUtilization) {
        Scheduler scheduler = schedule("""
                first: 2
                second: 3
                third: 4
                fourth: 5
                """, workers);

        assertEquals(workers, scheduler.workerCount());
        assertEquals(expectedMakespan, scheduler.makespan());
        assertEquals(expectedUtilization, scheduler.utilizationPercent());
        assertEquals(4, scheduler.assignments().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 64})
    void acceptsInclusiveWorkerCountBounds(int workers) {
        Scheduler scheduler = schedule("only: 0", workers);

        assertEquals(workers, scheduler.workerCount());
        assertEquals(0, scheduler.makespan());
        assertEquals(0, scheduler.utilizationPercent());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 65, -1, 100})
    void rejectsWorkerCountsOutsideInclusiveBounds(int workers) {
        Cpm cpm = cpm("only: 1");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> new Scheduler(cpm, workers));

        assertEquals("worker count must be from 1 to 64", error.getMessage());
    }

    @Test
    void prioritizesSmallerLatestStartBeforeEarlierFileLine() {
        Scheduler scheduler = schedule("""
                short: 1
                long: 4
                """, 1);

        assertEquals(List.of(
                expected("long", "1", 0, 4),
                expected("short", "1", 4, 5)), rows(scheduler));
        assertEquals(5, scheduler.makespan());
    }

    @Test
    void breaksEqualLatestStartPriorityByEarlierFileLine() {
        Scheduler scheduler = schedule("""
                first: 2
                second: 2
                third: 1
                """, 1);

        assertEquals(List.of(
                expected("first", "1", 0, 2),
                expected("second", "1", 2, 4),
                expected("third", "1", 4, 5)), rows(scheduler));
        assertEquals(5, scheduler.makespan());
    }

    @Test
    void assignsLowestFreeWorkerAfterSeveralTasksFinishTogether() {
        Scheduler scheduler = schedule("""
                a: 2
                b: 2
                c: 1 after a
                d: 1 after b
                """, 2);

        assertEquals(List.of(
                expected("a", "1", 0, 2),
                expected("b", "2", 0, 2),
                expected("c", "1", 2, 3),
                expected("d", "2", 2, 3)), rows(scheduler));
        assertEquals(3, scheduler.makespan());
    }

    @Test
    void finishesAllSimultaneousTasksBeforeDrainingZeroDurationSuccessors() {
        Scheduler scheduler = schedule("""
                left: 2
                right: 2
                gate: 0 after left right
                released: 1 after gate
                """, 2);

        assertEquals(List.of(
                expected("left", "1", 0, 2),
                expected("right", "2", 0, 2),
                expected("gate", "-", 2, 2),
                expected("released", "1", 2, 3)), rows(scheduler));
        assertEquals(3, scheduler.makespan());
    }

    @Test
    void completesZeroDurationReleaseChainAtTimeZeroBeforeStartingPositiveWork() {
        Scheduler scheduler = schedule("""
                first: 0
                second: 0 after first
                third: 0 after second
                work: 1 after third
                independent: 1
                """, 1);

        assertEquals(List.of(
                expected("first", "-", 0, 0),
                expected("second", "-", 0, 0),
                expected("third", "-", 0, 0),
                expected("work", "1", 0, 1),
                expected("independent", "1", 1, 2)), rows(scheduler));
        assertEquals(2, scheduler.makespan());
    }

    @Test
    void returnsZeroMakespanAndUtilizationWhenEveryTaskIsAMilestone() {
        Scheduler scheduler = schedule("""
                first: 0
                second: 0 after first
                """, 3);

        assertEquals(List.of(
                expected("first", "-", 0, 0),
                expected("second", "-", 0, 0)), rows(scheduler));
        assertEquals(0, scheduler.makespan());
        assertEquals(0, scheduler.utilizationPercent());
    }

    @ParameterizedTest
    @CsvSource({
            "1, 29",
            "3, 38",
            "4, 42"
    })
    void roundsUtilizationPercentHalfUp(long oneUnitTasks, long expectedPercent) {
        StringBuilder plan = new StringBuilder("anchor: 6\n");
        for (long index = 0; index < oneUnitTasks; index++) {
            plan.append("small").append(index).append(": 1\n");
        }

        Scheduler scheduler = schedule(plan.toString(), 4);

        assertEquals(6, scheduler.makespan());
        assertEquals(expectedPercent, scheduler.utilizationPercent());
    }

    @Test
    void handlesAnEmptyGraphWithoutInventingScheduledWork() {
        Scheduler scheduler = new Scheduler(new Cpm(new Graph(List.of())), 1);

        assertEquals(List.of(), scheduler.assignments());
        assertEquals(0, scheduler.makespan());
        assertEquals(0, scheduler.utilizationPercent());
    }

    @Test
    void schedulesAThreeThousandTaskChainIteratively() {
        StringBuilder plan = new StringBuilder();
        for (int index = 0; index < 3_000; index++) {
            plan.append("t").append(index).append(": 1");
            if (index > 0) {
                plan.append(" after t").append(index - 1);
            }
            plan.append('\n');
        }

        Scheduler scheduler = schedule(plan.toString(), 1);

        assertEquals(3_000, scheduler.assignments().size());
        assertEquals(3_000, scheduler.makespan());
        assertEquals(expected("t0", "1", 0, 1), row(scheduler, 0));
        assertEquals(expected("t2999", "1", 2_999, 3_000), row(scheduler, 2_999));
    }

    private static Scheduler schedule(String plan, int workers) {
        return new Scheduler(cpm(plan), workers);
    }

    private static Cpm cpm(String plan) {
        return new Cpm(new Graph(PlanParser.parse(plan, "plan")));
    }

    private static ExpectedAssignment expected(String task, String worker, long start, long finish) {
        return new ExpectedAssignment(task, worker, start, finish);
    }

    private static List<ExpectedAssignment> rows(Scheduler scheduler) {
        List<ExpectedAssignment> rows = new ArrayList<>();
        for (Scheduler.Assignment assignment : scheduler.assignments()) {
            rows.add(expected(assignment.task().name(), assignment.worker(),
                    assignment.start(), assignment.finish()));
        }
        return List.copyOf(rows);
    }

    private static ExpectedAssignment row(Scheduler scheduler, int index) {
        Scheduler.Assignment assignment = scheduler.assignments().get(index);
        return expected(assignment.task().name(), assignment.worker(), assignment.start(), assignment.finish());
    }
}
