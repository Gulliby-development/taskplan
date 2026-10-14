package io.github.gulliby.taskplan;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** One task from a plan, retaining its source line and dependency order. */
public record Task(String name, int duration, List<String> dependencies, int line) {
    public Task {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dependencies, "dependencies");
        dependencies = List.copyOf(new LinkedHashSet<>(dependencies));
    }
}
