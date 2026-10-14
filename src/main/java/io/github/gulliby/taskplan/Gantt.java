package io.github.gulliby.taskplan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Renders the earliest-start schedule as a scaled, text-based Gantt chart. */
public final class Gantt {
    private final Cpm cpm;

    public Gantt(Cpm cpm) {
        this.cpm = Objects.requireNonNull(cpm, "cpm");
    }

    /**
     * Renders one chart row per task, ordered by earliest start and then source line.
     *
     * @param scale the number of time units represented by one chart cell
     * @return immutable chart rows
     * @throws IllegalArgumentException if scale is not between 1 and 1000
     * @throws PlanException if the chart would exceed 200 cells
     */
    public List<String> render(int scale) {
        if (scale < 1 || scale > 1000) {
            throw new IllegalArgumentException("scale must be between 1 and 1000");
        }

        long cells = Math.max(1, ceilingDivide(cpm.length(), scale));
        if (cells > 200) {
            throw new PlanException("chart too wide (" + cells + " cells, limit 200; use --scale)");
        }

        List<Task> ordered = new ArrayList<>(cpm.tasks());
        ordered.sort(Comparator.comparingLong((Task task) -> cpm.earliestStart(task))
                .thenComparingInt(Task::line));
        int nameWidth = ordered.stream().mapToInt(task -> task.name().length()).max().orElse(0);
        List<String> rows = new ArrayList<>(ordered.size());
        for (Task task : ordered) {
            StringBuilder row = new StringBuilder();
            row.append(task.name());
            row.append(" ".repeat(nameWidth - task.name().length()));
            row.append(" |");
            appendCells(row, task, (int) cells, scale);
            row.append('|');
            rows.add(row.toString());
        }
        return List.copyOf(rows);
    }

    /** Convenience entry point for callers that do not need to retain a renderer. */
    public static List<String> render(Cpm cpm, int scale) {
        return new Gantt(cpm).render(scale);
    }

    private void appendCells(StringBuilder row, Task task, int cellCount, int scale) {
        long earliestStart = cpm.earliestStart(task);
        long earliestFinish = cpm.earliestFinish(task);
        long latestFinish = cpm.latestFinish(task);
        if (task.duration() == 0) {
            long milestoneCell = earliestStart / scale;
            int visibleCell = (int) Math.min(milestoneCell, cellCount - 1L);
            for (int cell = 0; cell < cellCount; cell++) {
                row.append(cell == visibleCell ? '*' : '.');
            }
            return;
        }

        for (int cell = 0; cell < cellCount; cell++) {
            long cellStart = (long) cell * scale;
            long cellFinish = cellStart + scale;
            if (overlaps(earliestStart, earliestFinish, cellStart, cellFinish)) {
                row.append('#');
            } else if (overlaps(earliestFinish, latestFinish, cellStart, cellFinish)) {
                row.append('-');
            } else {
                row.append('.');
            }
        }
    }

    private static boolean overlaps(long intervalStart, long intervalFinish,
                                    long cellStart, long cellFinish) {
        return intervalStart < cellFinish && intervalFinish > cellStart;
    }

    private static long ceilingDivide(long value, int divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }
}
