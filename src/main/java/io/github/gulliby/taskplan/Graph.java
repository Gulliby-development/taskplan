package io.github.gulliby.taskplan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/** Validates a plan's dependency graph and exposes its traversal order. */
public final class Graph {
    /** A graph diagnostic without its file display name. */
    public record Problem(int line, String code, String message) {
        public Problem {
            if (line < 1) {
                throw new IllegalArgumentException("line must be positive");
            }
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }

        /** Returns the line-prefixed form used after the plan's display name. */
        public String formatted() {
            return line + ": " + code + " " + message;
        }
    }

    private final List<Task> tasks;
    private final List<Task> uniqueTasks;
    private final Map<String, Task> taskByName;
    private final Map<String, List<String>> dependenciesByName;
    private final Map<String, List<Task>> successorsByName;
    private final List<Task> topologicalOrder;
    private final List<Problem> problems;

    /**
     * Builds graph indexes and computes all applicable diagnostics.
     * Duplicate task declarations after their first occurrence are ignored.
     */
    public Graph(List<Task> tasks) {
        this.tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));

        LinkedHashMap<String, Task> firstByName = new LinkedHashMap<>();
        List<Problem> diagnostics = new ArrayList<>();
        for (Task task : this.tasks) {
            Task first = firstByName.putIfAbsent(task.name(), task);
            if (first != null) {
                diagnostics.add(new Problem(task.line(), "TP001",
                        "duplicate task \"" + task.name() + "\" (first on line " + first.line() + ")"));
            }
        }
        this.taskByName = Collections.unmodifiableMap(firstByName);
        this.uniqueTasks = List.copyOf(firstByName.values());

        LinkedHashMap<String, List<String>> dependencies = new LinkedHashMap<>();
        LinkedHashMap<String, List<Task>> successors = new LinkedHashMap<>();
        for (Task task : uniqueTasks) {
            dependencies.put(task.name(), new ArrayList<>());
            successors.put(task.name(), new ArrayList<>());
        }
        for (Task task : uniqueTasks) {
            Set<String> seenUnknown = new LinkedHashSet<>();
            for (String dependency : task.dependencies()) {
                if (taskByName.containsKey(dependency)) {
                    dependencies.get(task.name()).add(dependency);
                    successors.get(dependency).add(task);
                } else if (seenUnknown.add(dependency)) {
                    diagnostics.add(new Problem(task.line(), "TP002",
                            "unknown dependency \"" + dependency + "\" of task \"" + task.name() + "\""));
                }
            }
        }
        this.dependenciesByName = immutableListMap(dependencies);
        this.successorsByName = immutableListMap(successors);

        diagnostics.addAll(findCycleProblems());
        List<Problem> structuralProblems = sortProblems(diagnostics);
        if (structuralProblems.isEmpty()) {
            diagnostics.addAll(findRedundantDependencyProblems());
        }
        this.problems = sortProblems(diagnostics);
        this.topologicalOrder = computeTopologicalOrder();
    }

    /** All declarations in input order, including later duplicate declarations. */
    public List<Task> tasks() {
        return tasks;
    }

    /** The first declaration of each task name, in file order. */
    public List<Task> uniqueTasks() {
        return uniqueTasks;
    }

    /** The dependency-first topological order; cyclic portions are omitted. */
    public List<Task> topologicalOrder() {
        return topologicalOrder;
    }

    /** Successor tasks keyed by task name, with each list in file order. */
    public Map<String, List<Task>> successors() {
        return successorsByName;
    }

    /** Successors of the named task, or an empty list for an unknown name. */
    public List<Task> successors(String taskName) {
        return successorsByName.getOrDefault(taskName, List.of());
    }

    /** Successors of the supplied task, looked up by its task name. */
    public List<Task> successors(Task task) {
        Objects.requireNonNull(task, "task");
        return successors(task.name());
    }

    /** All TP001–TP004 diagnostics in line and code order. */
    public List<Problem> problems() {
        return problems;
    }

    /** TP001–TP003 diagnostics, which prevent graph analysis commands. */
    public List<Problem> blockingProblems() {
        return problems.stream().filter(problem -> !problem.code().equals("TP004")).toList();
    }

    /** Whether the plan has any TP001–TP003 diagnostic. */
    public boolean hasBlockingProblems() {
        return problems.stream().anyMatch(problem -> !problem.code().equals("TP004"));
    }

    private List<Problem> findCycleProblems() {
        int count = uniqueTasks.size();
        Map<String, Integer> indexByName = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            indexByName.put(uniqueTasks.get(i).name(), i);
        }

        List<List<Integer>> edges = new ArrayList<>(count);
        List<List<Integer>> reverseEdges = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            edges.add(new ArrayList<>());
            reverseEdges.add(new ArrayList<>());
        }
        for (int i = 0; i < count; i++) {
            String name = uniqueTasks.get(i).name();
            for (String dependency : dependenciesByName.get(name)) {
                int dependencyIndex = indexByName.get(dependency);
                edges.get(i).add(dependencyIndex);
                reverseEdges.get(dependencyIndex).add(i);
            }
        }

        boolean[] visited = new boolean[count];
        int[] nextEdge = new int[count];
        int[] stack = new int[count];
        List<Integer> finished = new ArrayList<>(count);
        for (int root = 0; root < count; root++) {
            if (visited[root]) {
                continue;
            }
            int top = 0;
            stack[top] = root;
            visited[root] = true;
            while (top >= 0) {
                int node = stack[top];
                List<Integer> neighbours = edges.get(node);
                if (nextEdge[node] < neighbours.size()) {
                    int neighbour = neighbours.get(nextEdge[node]++);
                    if (!visited[neighbour]) {
                        visited[neighbour] = true;
                        stack[++top] = neighbour;
                    }
                } else {
                    finished.add(node);
                    top--;
                }
            }
        }

        List<Problem> cycleProblems = new ArrayList<>();
        java.util.Arrays.fill(visited, false);
        for (int finish = finished.size() - 1; finish >= 0; finish--) {
            int root = finished.get(finish);
            if (visited[root]) {
                continue;
            }
            List<Integer> component = new ArrayList<>();
            int top = 0;
            stack[top] = root;
            visited[root] = true;
            while (top >= 0) {
                int node = stack[top--];
                component.add(node);
                for (int neighbour : reverseEdges.get(node)) {
                    if (!visited[neighbour]) {
                        visited[neighbour] = true;
                        stack[++top] = neighbour;
                    }
                }
            }

            boolean selfCycle = component.size() == 1
                    && edges.get(component.get(0)).contains(component.get(0));
            if (component.size() > 1 || selfCycle) {
                component.sort(Comparator.naturalOrder());
                List<String> names = component.stream()
                        .map(index -> uniqueTasks.get(index).name())
                        .toList();
                Task firstInFile = uniqueTasks.get(component.get(0));
                cycleProblems.add(new Problem(firstInFile.line(), "TP003",
                        "dependency cycle among " + quoteNames(names)));
            }
        }
        return cycleProblems;
    }

    private List<Problem> findRedundantDependencyProblems() {
        List<Problem> redundant = new ArrayList<>();
        for (Task task : uniqueTasks) {
            List<String> directDependencies = dependenciesByName.get(task.name());
            if (directDependencies.size() < 2) {
                continue;
            }
            Set<String> directNames = new HashSet<>(directDependencies);
            Map<String, String> impliedBy = new LinkedHashMap<>();

            for (String possibleImplyingDependency : directDependencies) {
                List<String> itsDependencies = dependenciesByName.get(possibleImplyingDependency);
                if (itsDependencies.isEmpty()) {
                    continue;
                }

                Set<String> visited = new HashSet<>();
                List<String> stack = new ArrayList<>(itsDependencies);
                while (!stack.isEmpty()) {
                    String reachable = stack.remove(stack.size() - 1);
                    if (!visited.add(reachable)) {
                        continue;
                    }
                    if (!reachable.equals(possibleImplyingDependency) && directNames.contains(reachable)) {
                        impliedBy.putIfAbsent(reachable, possibleImplyingDependency);
                    }
                    stack.addAll(dependenciesByName.get(reachable));
                }
            }

            for (String dependency : directDependencies) {
                String implyingDependency = impliedBy.get(dependency);
                if (implyingDependency != null) {
                    redundant.add(new Problem(task.line(), "TP004",
                            "dependency \"" + dependency + "\" of task \"" + task.name()
                                    + "\" is already implied by \"" + implyingDependency + "\""));
                }
            }
        }
        return redundant;
    }

    private List<Task> computeTopologicalOrder() {
        Map<String, Integer> indexByName = new LinkedHashMap<>();
        int[] remainingDependencies = new int[uniqueTasks.size()];
        for (int i = 0; i < uniqueTasks.size(); i++) {
            Task task = uniqueTasks.get(i);
            indexByName.put(task.name(), i);
            remainingDependencies[i] = dependenciesByName.get(task.name()).size();
        }

        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < remainingDependencies.length; i++) {
            if (remainingDependencies[i] == 0) {
                ready.add(i);
            }
        }

        List<Task> ordered = new ArrayList<>(uniqueTasks.size());
        while (!ready.isEmpty()) {
            int index = ready.remove();
            Task task = uniqueTasks.get(index);
            ordered.add(task);
            for (Task successor : successorsByName.get(task.name())) {
                int successorIndex = indexByName.get(successor.name());
                remainingDependencies[successorIndex]--;
                if (remainingDependencies[successorIndex] == 0) {
                    ready.add(successorIndex);
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static String quoteNames(List<String> names) {
        return names.stream().map(name -> "\"" + name + "\"").reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static List<Problem> sortProblems(List<Problem> problems) {
        List<Problem> sorted = new ArrayList<>(problems);
        sorted.sort(Comparator.comparingInt(Problem::line).thenComparing(Problem::code));
        return List.copyOf(sorted);
    }

    private static <T> Map<String, List<T>> immutableListMap(Map<String, List<T>> source) {
        LinkedHashMap<String, List<T>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }
}
