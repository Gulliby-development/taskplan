package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GraphTest {
    @Test
    void reportsDuplicateDeclarationAndIgnoresItForGraphConstruction() {
        Graph graph = new Graph(List.of(
                task("build", 2, List.of(), 1),
                task("test", 3, List.of("build"), 2),
                task("build", 99, List.of("missing"), 3)));

        assertEquals(List.of("3: TP001 duplicate task \"build\" (first on line 1)"),
                formatted(graph.problems()));
        assertEquals(List.of("build", "test"), names(graph.uniqueTasks()));
        assertEquals(List.of("build", "test"), names(graph.topologicalOrder()));
        assertEquals(List.of(task("test", 3, List.of("build"), 2)), graph.successors("build"));
    }

    @Test
    void reportsUnknownDependenciesOncePerNameAndAtTheTaskLine() {
        Graph graph = new Graph(List.of(
                task("first", 1, List.of("missing", "missing", "other"), 7)));

        assertEquals(List.of(
                "7: TP002 unknown dependency \"missing\" of task \"first\"",
                "7: TP002 unknown dependency \"other\" of task \"first\""),
                formatted(graph.problems()));
        assertTrue(graph.hasBlockingProblems());
        assertEquals(graph.problems(), graph.blockingProblems());
    }

    @Test
    void reportsMultiTaskAndSelfCyclesAsSeparateComponents() {
        Graph graph = new Graph(List.of(
                task("a", 1, List.of("c"), 1),
                task("b", 1, List.of("a"), 2),
                task("c", 1, List.of("b"), 3),
                task("d", 1, List.of("d"), 4),
                task("free", 1, List.of(), 5)));

        assertEquals(List.of(
                "1: TP003 dependency cycle among \"a\", \"b\", \"c\"",
                "4: TP003 dependency cycle among \"d\""),
                formatted(graph.problems()));
        assertEquals(List.of("free"), names(graph.topologicalOrder()));
    }

    @Test
    void ordersStructuralDiagnosticsByLineThenCode() {
        Graph graph = new Graph(List.of(
                task("dup", 1, List.of(), 1),
                task("dup", 2, List.of("ignored"), 2),
                task("root", 1, List.of("absent", "absent"), 3),
                task("cycleA", 1, List.of("unknown", "cycleB"), 4),
                task("cycleB", 1, List.of("cycleA"), 5),
                task("self", 1, List.of("self"), 6)));

        assertEquals(List.of(
                "2: TP001 duplicate task \"dup\" (first on line 1)",
                "3: TP002 unknown dependency \"absent\" of task \"root\"",
                "4: TP002 unknown dependency \"unknown\" of task \"cycleA\"",
                "4: TP003 dependency cycle among \"cycleA\", \"cycleB\"",
                "6: TP003 dependency cycle among \"self\""),
                formatted(graph.problems()));
    }

    @Test
    void repeatedDependencyNamesProduceOnlyOneSuccessorEdge() {
        Task target = task("target", 1, List.of("base", "base", "base"), 2);
        Graph graph = new Graph(List.of(task("base", 1, List.of(), 1), target));

        assertEquals(List.of("base"), target.dependencies());
        assertEquals(List.of(target), graph.successors("base"));
        assertEquals(List.of("base", "target"), names(graph.topologicalOrder()));
        assertTrue(graph.problems().isEmpty());
    }

    @Test
    void reportsRedundantDependenciesUsingTheFirstImplyingDependency() {
        Graph graph = new Graph(List.of(
                task("root", 1, List.of(), 1),
                task("bridge", 1, List.of("root"), 2),
                task("middle", 1, List.of("bridge"), 3),
                task("target", 1, List.of("root", "middle", "bridge"), 4)));

        assertEquals(List.of(
                "4: TP004 dependency \"root\" of task \"target\" is already implied by \"middle\"",
                "4: TP004 dependency \"bridge\" of task \"target\" is already implied by \"middle\""),
                formatted(graph.problems()));
        assertFalse(graph.hasBlockingProblems());
        assertTrue(graph.blockingProblems().isEmpty());
    }

    @Test
    void detectsRedundancyThroughMoreThanOneDependencyEdge() {
        Graph graph = new Graph(List.of(
                task("base", 1, List.of(), 1),
                task("middle", 1, List.of("base"), 2),
                task("upper", 1, List.of("middle"), 3),
                task("finish", 1, List.of("base", "upper"), 4)));

        assertEquals(List.of(
                "4: TP004 dependency \"base\" of task \"finish\" is already implied by \"upper\""),
                formatted(graph.problems()));
    }

    @Test
    void suppressesRedundancyDiagnosticsWhenAnyStructuralProblemExists() {
        Graph graph = new Graph(List.of(
                task("base", 1, List.of(), 1),
                task("middle", 1, List.of("base"), 2),
                task("finish", 1, List.of("base", "middle", "missing"), 3)));

        assertEquals(List.of(
                "3: TP002 unknown dependency \"missing\" of task \"finish\""),
                formatted(graph.problems()));
        assertTrue(graph.blockingProblems().containsAll(graph.problems()));
    }

    @Test
    void exposesSuccessorsInFileOrderAndAsImmutableLists() {
        Task later = task("later", 1, List.of("base"), 3);
        Task earlier = task("earlier", 1, List.of("base"), 2);
        Graph graph = new Graph(List.of(task("base", 1, List.of(), 1), earlier, later));

        assertEquals(List.of(earlier, later), graph.successors("base"));
        assertEquals(List.of(earlier, later), graph.successors(task("base", 0, List.of(), 99)));
        assertEquals(Map.of("base", List.of(earlier, later), "earlier", List.of(), "later", List.of()),
                graph.successors());
        assertThrows(UnsupportedOperationException.class, () -> graph.successors("base").add(later));
        assertThrows(UnsupportedOperationException.class, () -> graph.successors().put("other", List.of()));
    }

    @Test
    void computesStableDependencyFirstTopologicalOrder() {
        Graph graph = new Graph(List.of(
                task("rootA", 1, List.of(), 1),
                task("childA", 1, List.of("rootA"), 2),
                task("rootB", 1, List.of(), 3),
                task("childB", 1, List.of("rootB"), 4),
                task("join", 1, List.of("childA", "childB"), 5)));

        assertEquals(List.of("rootA", "childA", "rootB", "childB", "join"),
                names(graph.topologicalOrder()));
    }

    @Test
    void reportsAcyclicPortionOnlyInTopologicalOrderWhenGraphHasCycles() {
        Graph graph = new Graph(List.of(
                task("cycleA", 1, List.of("cycleB"), 1),
                task("cycleB", 1, List.of("cycleA"), 2),
                task("free", 1, List.of(), 3),
                task("blocked", 1, List.of("cycleA"), 4)));

        assertEquals(List.of("free"), names(graph.topologicalOrder()));
        assertEquals("1: TP003 dependency cycle among \"cycleA\", \"cycleB\"",
                formatted(graph.problems()).get(0));
    }

    @Test
    void handlesAnEmptyGraphWithoutDiagnostics() {
        Graph graph = new Graph(List.of());

        assertTrue(graph.tasks().isEmpty());
        assertTrue(graph.uniqueTasks().isEmpty());
        assertTrue(graph.topologicalOrder().isEmpty());
        assertTrue(graph.problems().isEmpty());
        assertFalse(graph.hasBlockingProblems());
    }

    @Test
    void traversesTwentyThousandTaskChainWithoutRecursion() {
        int taskCount = 20_000;
        List<Task> tasks = new ArrayList<>(taskCount);
        for (int index = 0; index < taskCount; index++) {
            String name = "task" + index;
            List<String> dependencies = index == 0 ? List.of() : List.of("task" + (index - 1));
            tasks.add(task(name, 1, dependencies, index + 1));
        }

        Graph graph = new Graph(tasks);

        assertTrue(graph.problems().isEmpty());
        assertEquals(taskCount, graph.topologicalOrder().size());
        assertEquals("task0", graph.topologicalOrder().get(0).name());
        assertEquals("task19999", graph.topologicalOrder().get(taskCount - 1).name());
    }

    private static Task task(String name, int duration, List<String> dependencies, int line) {
        return new Task(name, duration, dependencies, line);
    }

    private static List<String> formatted(List<Graph.Problem> problems) {
        return problems.stream().map(Graph.Problem::formatted).toList();
    }

    private static List<String> names(List<Task> tasks) {
        return tasks.stream().map(Task::name).toList();
    }
}
