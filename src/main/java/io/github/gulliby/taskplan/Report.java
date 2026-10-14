package io.github.gulliby.taskplan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Formats all user-visible taskplan reports. */
public final class Report {
    private Report() {
    }

    /** Returns the four usage lines. */
    public static List<String> usage() {
        return List.of(Options.USAGE.split("\\n", -1));
    }

    /** Formats the successful or diagnostic output for the check command. */
    public static List<String> check(String display, Graph graph) {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(graph, "graph");
        if (!graph.problems().isEmpty()) {
            return problems(display, graph.problems());
        }
        int taskCount = graph.uniqueTasks().size();
        return List.of(display + ": ok, " + taskCount + (taskCount == 1 ? " task" : " tasks"));
    }

    /** Formats graph problems with the file display name. */
    public static List<String> problems(String display, List<Graph.Problem> problems) {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(problems, "problems");
        return problems.stream()
                .map(problem -> display + ":" + Objects.requireNonNull(problem, "problem").formatted())
                .toList();
    }

    /** Formats the critical-path table and project summary. */
    public static List<String> critical(Cpm cpm) {
        Objects.requireNonNull(cpm, "cpm");
        List<Task> tasks = cpm.tasks();
        List<String[]> rows = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            rows.add(new String[] {
                    task.name(),
                    Integer.toString(task.duration()),
                    Long.toString(cpm.earliestStart(task)),
                    Long.toString(cpm.earliestFinish(task)),
                    Long.toString(cpm.slack(task))
            });
        }
        int[] widths = columnWidths(new String[] {"TASK", "DUR", "START", "FINISH", "SLACK"}, rows);
        List<String> output = new ArrayList<>(rows.size() + 3);
        output.add(formatRow(new String[] {"TASK", "DUR", "START", "FINISH", "SLACK"}, widths));
        for (String[] row : rows) {
            output.add(formatRow(row, widths));
        }
        output.add("length: " + cpm.length());
        String criticalTasks = cpm.criticalTasks().stream()
                .map(Task::name)
                .collect(Collectors.joining(", "));
        output.add("critical tasks: " + criticalTasks);
        return List.copyOf(output);
    }

    /** Formats the worker assignment table and simulation summary. */
    public static List<String> schedule(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        List<Scheduler.Assignment> assignments = scheduler.assignments();
        List<String[]> rows = new ArrayList<>(assignments.size());
        for (Scheduler.Assignment assignment : assignments) {
            Task task = assignment.task();
            rows.add(new String[] {
                    task.name(),
                    assignment.worker(),
                    Long.toString(assignment.start()),
                    Long.toString(assignment.finish())
            });
        }
        String[] header = {"TASK", "WORKER", "START", "FINISH"};
        int[] widths = columnWidths(header, rows);
        List<String> output = new ArrayList<>(rows.size() + 4);
        output.add(formatRow(header, widths));
        for (String[] row : rows) {
            output.add(formatRow(row, widths));
        }
        output.add("makespan: " + scheduler.makespan());
        output.add("workers: " + scheduler.workerCount());
        output.add("utilization: " + scheduler.utilizationPercent() + "%");
        return List.copyOf(output);
    }

    /** Renders the scaled Gantt chart. */
    public static List<String> gantt(Cpm cpm, int scale) {
        return Gantt.render(Objects.requireNonNull(cpm, "cpm"), scale);
    }

    private static int[] columnWidths(String[] header, List<String[]> rows) {
        int[] widths = new int[header.length];
        for (int column = 0; column < header.length; column++) {
            widths[column] = header[column].length();
        }
        for (String[] row : rows) {
            for (int column = 0; column < row.length; column++) {
                widths[column] = Math.max(widths[column], row[column].length());
            }
        }
        return widths;
    }

    private static String formatRow(String[] cells, int[] widths) {
        StringBuilder row = new StringBuilder();
        for (int column = 0; column < cells.length; column++) {
            if (column > 0) {
                row.append("  ");
            }
            row.append(column == 0
                    ? padRight(cells[column], widths[column])
                    : padLeft(cells[column], widths[column]));
        }
        return row.toString();
    }

    private static String padRight(String value, int width) {
        return value + " ".repeat(width - value.length());
    }

    private static String padLeft(String value, int width) {
        return " ".repeat(width - value.length()) + value;
    }
}
