package io.github.gulliby.taskplan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** Simulates deterministic assignment of a valid plan to a fixed set of workers. */
public final class Scheduler {
    /** One task's scheduled interval; a worker of {@code "-"} denotes a zero-duration task. */
    public record Assignment(Task task, String worker, long start, long finish) {
        public Assignment {
            Objects.requireNonNull(task, "task");
            Objects.requireNonNull(worker, "worker");
            if (start < 0 || finish < start) {
                throw new IllegalArgumentException("invalid assignment interval");
            }
        }
    }

    private record Running(Assignment assignment, int workerNumber) {
    }

    private final int workerCount;
    private final List<Assignment> assignments;
    private final long makespan;
    private final long utilizationPercent;

    /**
     * Runs the worker simulation for the tasks represented by {@code cpm}.
     *
     * @param cpm the critical-path analysis of a valid plan
     * @param workerCount number of available workers, from 1 through 64
     */
    public Scheduler(Cpm cpm, int workerCount) {
        Objects.requireNonNull(cpm, "cpm");
        if (workerCount < 1 || workerCount > 64) {
            throw new IllegalArgumentException("worker count must be from 1 to 64");
        }
        this.workerCount = workerCount;

        List<Task> tasks = cpm.tasks();
        Map<String, Integer> remainingDependencies = new HashMap<>();
        Map<String, List<Task>> successors = new HashMap<>();
        for (Task task : tasks) {
            remainingDependencies.put(task.name(), task.dependencies().size());
            successors.put(task.name(), new ArrayList<>());
        }
        for (Task task : tasks) {
            for (String dependency : task.dependencies()) {
                List<Task> dependentTasks = successors.get(dependency);
                if (dependentTasks == null) {
                    throw new IllegalArgumentException("unknown dependency: " + dependency);
                }
                dependentTasks.add(task);
            }
        }

        Comparator<Task> taskPriority = Comparator
                .comparingLong((Task task) -> cpm.latestStart(task))
                .thenComparingInt(Task::line);
        PriorityQueue<Task> readyZeroDuration = new PriorityQueue<>(taskPriority);
        PriorityQueue<Task> readyPositiveDuration = new PriorityQueue<>(taskPriority);
        for (Task task : tasks) {
            if (remainingDependencies.get(task.name()) == 0) {
                enqueueReady(task, readyZeroDuration, readyPositiveDuration);
            }
        }

        PriorityQueue<Integer> freeWorkers = new PriorityQueue<>();
        for (int worker = 1; worker <= workerCount; worker++) {
            freeWorkers.add(worker);
        }
        Comparator<Running> completionOrder = Comparator
                .comparingLong((Running item) -> item.assignment().finish())
                .thenComparingInt(item -> item.assignment().task().line());
        PriorityQueue<Running> running = new PriorityQueue<>(completionOrder);
        List<Assignment> scheduled = new ArrayList<>(tasks.size());
        long totalDuration = 0;
        long projectMakespan = 0;
        for (Task task : tasks) {
            totalDuration += task.duration();
        }

        long now = 0;
        int completedCount = 0;
        while (completedCount < tasks.size()) {
            List<Running> finishingNow = new ArrayList<>();
            while (!running.isEmpty() && running.peek().assignment().finish() == now) {
                Running finished = running.remove();
                freeWorkers.add(finished.workerNumber());
                finishingNow.add(finished);
            }
            for (Running finished : finishingNow) {
                completedCount = releaseSuccessors(finished.assignment().task(), completedCount,
                        successors, remainingDependencies, readyZeroDuration, readyPositiveDuration);
            }

            while (!readyZeroDuration.isEmpty()) {
                Task task = readyZeroDuration.remove();
                scheduled.add(new Assignment(task, "-", now, now));
                completedCount = releaseSuccessors(task, completedCount,
                        successors, remainingDependencies, readyZeroDuration, readyPositiveDuration);
            }

            while (!freeWorkers.isEmpty() && !readyPositiveDuration.isEmpty()) {
                Task task = readyPositiveDuration.remove();
                int worker = freeWorkers.remove();
                long finish = now + task.duration();
                Assignment assignment = new Assignment(task, Integer.toString(worker), now, finish);
                scheduled.add(assignment);
                running.add(new Running(assignment, worker));
                projectMakespan = Math.max(projectMakespan, finish);
            }

            if (completedCount == tasks.size()) {
                break;
            }
            if (running.isEmpty()) {
                throw new IllegalStateException("valid task graph has no schedulable task");
            }
            now = running.peek().assignment().finish();
        }

        scheduled.sort(Comparator.comparingLong(Assignment::start)
                .thenComparingInt(assignment -> assignment.task().line()));
        this.assignments = List.copyOf(scheduled);
        this.makespan = projectMakespan;
        this.utilizationPercent = calculateUtilization(totalDuration, workerCount, projectMakespan);
    }

    /** Returns scheduled rows ordered by start time, then original file line. */
    public List<Assignment> assignments() {
        return assignments;
    }

    /** Returns the number of workers used in the simulation. */
    public int workerCount() {
        return workerCount;
    }

    /** Returns the time at which the last task finishes, or zero when no time elapses. */
    public long makespan() {
        return makespan;
    }

    /** Returns total worker utilization as a whole percentage rounded half up. */
    public long utilizationPercent() {
        return utilizationPercent;
    }

    private static int releaseSuccessors(Task completed, int completedCount,
            Map<String, List<Task>> successors, Map<String, Integer> remainingDependencies,
            PriorityQueue<Task> readyZeroDuration, PriorityQueue<Task> readyPositiveDuration) {
        for (Task successor : successors.get(completed.name())) {
            int remaining = remainingDependencies.compute(successor.name(),
                    (name, count) -> Objects.requireNonNull(count, "dependency count") - 1);
            if (remaining == 0) {
                enqueueReady(successor, readyZeroDuration, readyPositiveDuration);
            }
        }
        return completedCount + 1;
    }

    private static void enqueueReady(Task task, PriorityQueue<Task> readyZeroDuration,
            PriorityQueue<Task> readyPositiveDuration) {
        if (task.duration() == 0) {
            readyZeroDuration.add(task);
        } else {
            readyPositiveDuration.add(task);
        }
    }

    private static long calculateUtilization(long totalDuration, int workerCount, long makespan) {
        if (makespan == 0) {
            return 0;
        }
        long numerator = totalDuration * 100;
        long denominator = (long) workerCount * makespan;
        long wholePercent = numerator / denominator;
        long remainder = numerator % denominator;
        long halfDenominatorRoundedUp = denominator / 2 + denominator % 2;
        return wholePercent + (remainder >= halfDenominatorRoundedUp ? 1 : 0);
    }
}
