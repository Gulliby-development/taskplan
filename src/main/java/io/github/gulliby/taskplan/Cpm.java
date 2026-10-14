package io.github.gulliby.taskplan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Computes earliest and latest task times for a valid dependency graph. */
public final class Cpm {
    private final List<Task> tasks;
    private final Map<String, Task> tasksByName;
    private final Map<String, Long> earliestStarts;
    private final Map<String, Long> earliestFinishes;
    private final Map<String, Long> latestStarts;
    private final Map<String, Long> latestFinishes;
    private final Map<String, Long> slacks;
    private final long length;
    private final List<Task> criticalTasks;

    /**
     * Computes all CPM values in dependency-first and successor-first topological order.
     *
     * @param graph the validated dependency graph to analyze
     * @throws IllegalArgumentException if the graph has blocking diagnostics
     */
    public Cpm(Graph graph) {
        Objects.requireNonNull(graph, "graph");
        if (graph.hasBlockingProblems()) {
            throw new IllegalArgumentException("graph has blocking problems");
        }

        this.tasks = graph.uniqueTasks();
        LinkedHashMap<String, Task> byName = new LinkedHashMap<>();
        for (Task task : tasks) {
            byName.put(task.name(), task);
        }
        this.tasksByName = Collections.unmodifiableMap(byName);

        LinkedHashMap<String, Long> es = new LinkedHashMap<>();
        LinkedHashMap<String, Long> ef = new LinkedHashMap<>();
        long projectLength = 0;
        for (Task task : graph.topologicalOrder()) {
            long start = 0;
            for (String dependency : task.dependencies()) {
                start = Math.max(start, ef.get(dependency));
            }
            long finish = start + task.duration();
            es.put(task.name(), start);
            ef.put(task.name(), finish);
            projectLength = Math.max(projectLength, finish);
        }
        if (es.size() != tasks.size()) {
            throw new IllegalArgumentException("graph must be acyclic");
        }

        LinkedHashMap<String, Long> ls = new LinkedHashMap<>();
        LinkedHashMap<String, Long> lf = new LinkedHashMap<>();
        LinkedHashMap<String, Long> slack = new LinkedHashMap<>();
        List<Task> reverseTopologicalOrder = new ArrayList<>(graph.topologicalOrder());
        Collections.reverse(reverseTopologicalOrder);
        for (Task task : reverseTopologicalOrder) {
            long finish = projectLength;
            for (Task successor : graph.successors(task)) {
                finish = Math.min(finish, ls.get(successor.name()));
            }
            long start = finish - task.duration();
            lf.put(task.name(), finish);
            ls.put(task.name(), start);
            slack.put(task.name(), start - es.get(task.name()));
        }

        this.earliestStarts = immutableMap(es);
        this.earliestFinishes = immutableMap(ef);
        this.latestStarts = immutableMap(ls);
        this.latestFinishes = immutableMap(lf);
        this.slacks = immutableMap(slack);
        this.length = projectLength;

        List<Task> critical = new ArrayList<>();
        for (Task task : tasks) {
            if (slack.get(task.name()) == 0) {
                critical.add(task);
            }
        }
        critical.sort(Comparator.comparingLong((Task task) -> es.get(task.name()))
                .thenComparingInt(Task::line));
        this.criticalTasks = List.copyOf(critical);
    }

    /** Returns the project length, the largest earliest finish. */
    public long length() {
        return length;
    }

    /** Returns tasks in their original file order, excluding duplicate declarations. */
    public List<Task> tasks() {
        return tasks;
    }

    /** Returns the task's earliest start time. */
    public long earliestStart(Task task) {
        return earliestStart(requireTask(task).name());
    }

    /** Returns the named task's earliest start time. */
    public long earliestStart(String taskName) {
        return value(earliestStarts, taskName);
    }

    /** Returns the task's earliest finish time. */
    public long earliestFinish(Task task) {
        return earliestFinish(requireTask(task).name());
    }

    /** Returns the named task's earliest finish time. */
    public long earliestFinish(String taskName) {
        return value(earliestFinishes, taskName);
    }

    /** Returns the task's latest start time without delaying the project. */
    public long latestStart(Task task) {
        return latestStart(requireTask(task).name());
    }

    /** Returns the named task's latest start time without delaying the project. */
    public long latestStart(String taskName) {
        return value(latestStarts, taskName);
    }

    /** Returns the task's latest finish time without delaying the project. */
    public long latestFinish(Task task) {
        return latestFinish(requireTask(task).name());
    }

    /** Returns the named task's latest finish time without delaying the project. */
    public long latestFinish(String taskName) {
        return value(latestFinishes, taskName);
    }

    /** Returns the task's available slack. */
    public long slack(Task task) {
        return slack(requireTask(task).name());
    }

    /** Returns the named task's available slack. */
    public long slack(String taskName) {
        return value(slacks, taskName);
    }

    /** Returns all earliest start times in task file order. */
    public Map<String, Long> earliestStarts() {
        return earliestStarts;
    }

    /** Returns all earliest finish times in task file order. */
    public Map<String, Long> earliestFinishes() {
        return earliestFinishes;
    }

    /** Returns all latest start times in task file order. */
    public Map<String, Long> latestStarts() {
        return latestStarts;
    }

    /** Returns all latest finish times in task file order. */
    public Map<String, Long> latestFinishes() {
        return latestFinishes;
    }

    /** Returns all slack values in task file order. */
    public Map<String, Long> slacks() {
        return slacks;
    }

    /** Returns zero-slack tasks ordered by earliest start, then source line. */
    public List<Task> criticalTasks() {
        return criticalTasks;
    }

    private Task requireTask(Task task) {
        Objects.requireNonNull(task, "task");
        return requireTask(task.name());
    }

    private Task requireTask(String taskName) {
        Objects.requireNonNull(taskName, "taskName");
        Task task = tasksByName.get(taskName);
        if (task == null) {
            throw new IllegalArgumentException("unknown task: " + taskName);
        }
        return task;
    }

    private long value(Map<String, Long> values, String taskName) {
        Task task = requireTask(taskName);
        return values.get(task.name());
    }

    private static Map<String, Long> immutableMap(Map<String, Long> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
