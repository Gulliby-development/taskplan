package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DocsTest {
    @Test
    void describesThePlannerAndItsAudience() throws IOException {
        String readme = readme();

        assertContains(readme, "offline command-line planner");
        assertContains(readme, "builds, releases, migrations");
        assertContains(readme, "other work");
    }

    @Test
    void includesEveryRequiredHeading() throws IOException {
        String readme = readme();

        assertContains(readme, "## Install");
        assertContains(readme, "## Usage");
        assertContains(readme, "## Plan file");
        assertContains(readme, "## Problems");
        assertContains(readme, "## Exit codes");
        assertContains(readme, "## Development");
    }

    @Test
    void documentsBuildAndLaunchCommands() throws IOException {
        String readme = readme();

        assertContains(readme, "mvn -B -q verify");
        assertContains(readme, "java -cp target/classes io.github.gulliby.taskplan.Main ARGS");
    }

    @Test
    void includesTheExactUsageLines() throws IOException {
        assertContains(readme(), """
                ```text
                usage: taskplan check FILE
                       taskplan critical FILE
                       taskplan schedule [--workers N] FILE
                       taskplan gantt [--scale K] FILE
                ```
                """);
    }

    @Test
    void includesTheExactPlanFileExample() throws IOException {
        assertContains(readme(), """
                ```text
                # release pipeline
                build: 5
                test: 8 after build
                lint: 2 after build
                package: 3 after test lint
                deploy: 1 after package
                docs: 4
                ```
                """);
    }

    @Test
    void includesTheWorkedCheckResult() throws IOException {
        assertContains(readme(), """
                ```text
                plan.txt: ok, 6 tasks
                ```
                """);
    }

    @Test
    void includesTheExactCriticalPathOutput() throws IOException {
        assertContains(readme(), """
                ```text
                TASK     DUR  START  FINISH  SLACK
                build      5      0       5      0
                test       8      5      13      0
                lint       2      5       7      6
                package    3     13      16      0
                deploy     1     16      17      0
                docs       4      0       4     13
                length: 17
                critical tasks: build, test, package, deploy
                ```
                """);
    }

    @Test
    void includesTheExactScheduleOutput() throws IOException {
        assertContains(readme(), """
                ```text
                TASK     WORKER  START  FINISH
                build         1      0       5
                docs          2      0       4
                test          1      5      13
                lint          2      5       7
                package       1     13      16
                deploy        1     16      17
                makespan: 17
                workers: 2
                utilization: 68%
                ```
                """);
    }

    @Test
    void includesTheExactGanttOutput() throws IOException {
        assertContains(readme(), """
                ```text
                build   |#####............|
                docs    |####-------------|
                test    |.....########....|
                lint    |.....##------....|
                package |.............###.|
                deploy  |................#|
                ```
                """);
    }

    @Test
    void explainsThePlanFormatAndProblemCodes() throws IOException {
        String readme = readme();

        assertContains(readme, "UTF-8");
        assertContains(readme, "`NAME: DURATION`");
        assertContains(readme, "LF and CRLF");
        assertContains(readme, "TP001");
        assertContains(readme, "TP002");
        assertContains(readme, "TP003");
        assertContains(readme, "TP004");
    }

    @Test
    void explainsSchedulingPriorityAndExitCodes() throws IOException {
        String readme = readme();

        assertContains(readme, "latest-start time");
        assertContains(readme, "order in the file");
        assertContains(readme, "lowest-numbered free worker");
        assertContains(readme, "`0`: success");
        assertContains(readme, "`1`: plan problems");
        assertContains(readme, "`2`: usage or input error");
    }

    private static String readme() throws IOException {
        return Files.readString(Path.of("README.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static void assertContains(String readme, String expected) {
        assertTrue(readme.contains(expected), () -> "README.md is missing required content:\n" + expected);
    }
}
