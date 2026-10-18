<p align="center"><img src="docs/logo.svg" width="96" alt="" /></p>

# taskplan

taskplan is an offline command-line planner for sequencing builds, releases, migrations, and other work. It validates task dependencies, calculates critical paths, simulates worker assignments, and draws a Gantt chart.

[![CI](https://github.com/Gulliby-development/taskplan/actions/workflows/ci.yml/badge.svg)](https://github.com/Gulliby-development/taskplan/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Why

Reach for it when you have a list of steps with durations and "after" relations and want to know whether the plan is sound, which steps decide the finish date, and how long it takes with a given number of people or machines. It needs only a JDK and reads one text file.

## Install

Use JDK 21 and Maven. From the project directory, run:

```text
mvn -B -q verify
```

Run the CLI with:

```text
java -cp target/classes io.github.gulliby.taskplan.Main ARGS
```

Replace `ARGS` with one of the commands below.

## Usage

Run `--help` by itself to print:

```text
usage: taskplan check FILE
       taskplan critical [--max-slack N] FILE
       taskplan schedule [--workers N] FILE
       taskplan gantt [--scale K] FILE
       taskplan order FILE
```

The following worked example uses `plan.txt`:

```text
# release pipeline
build: 5
test: 8 after build
lint: 2 after build
package: 3 after test lint
deploy: 1 after package
docs: 4
```

Check the file:

```text
java -cp target/classes io.github.gulliby.taskplan.Main check plan.txt
```

```text
plan.txt: ok, 6 tasks
```

Show the critical-path report:

```text
java -cp target/classes io.github.gulliby.taskplan.Main critical plan.txt
```

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

Add `--max-slack N` (a whole number from 0 to 100000) to list only the tasks whose slack is at most N; `0` lists the critical tasks only. The `length:` and `critical tasks:` lines stay as they are:

```text
java -cp target/classes io.github.gulliby.taskplan.Main critical --max-slack 0 plan.txt
```

```text
TASK     DUR  START  FINISH  SLACK
build      5      0       5      0
test       8      5      13      0
package    3     13      16      0
deploy     1     16      17      0
length: 17
critical tasks: build, test, package, deploy
```

Simulate assignments (the default is two workers):

```text
java -cp target/classes io.github.gulliby.taskplan.Main schedule plan.txt
```

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

Draw the Gantt chart:

```text
java -cp target/classes io.github.gulliby.taskplan.Main gantt plan.txt
```

```text
build   |#####............|
docs    |####-------------|
test    |.....########....|
lint    |.....##------....|
package |.............###.|
deploy  |................#|
```

List the tasks in an order that can be followed from top to bottom, one name per line:

```text
java -cp target/classes io.github.gulliby.taskplan.Main order plan.txt
```

```text
build
test
lint
package
deploy
docs
```

Every task comes after all of its dependencies. When several tasks are ready, the one that is first in the file comes first. `order` takes no options and stops on the same plan problems as `critical`, `schedule` and `gantt`.

Use `--workers N` to set the worker count for `schedule`, `--scale K` to set the time scale for `gantt`, or `--max-slack N` to limit the tasks listed by `critical`.

## Plan file

A plan is UTF-8 text. Each task line has the form `NAME: DURATION` or `NAME: DURATION after DEP DEP...`. Names contain 1–40 ASCII letters, digits, underscores, periods, or hyphens. Durations are decimal values from 0 to 100000. Separate dependency names with spaces or tabs. Blank lines and lines whose trimmed content begins with `#` are ignored. Both LF and CRLF line endings are supported. A dependency name repeated in one list counts once.

Use `-` as FILE to read the plan from standard input.

## Problems

`check` reports problems in file order. The other analysis commands stop on duplicate tasks, unknown dependencies, or cycles; redundant dependencies are reported by `check` and ignored by those commands.

- TP001: duplicate task name.
- TP002: dependency names a task that does not exist.
- TP003: dependency cycle, including a task depending on itself.
- TP004: dependency is redundant because another dependency already depends on it.

## Scheduler

The scheduler prioritizes ready tasks by earliest latest-start time, then by their order in the file. It assigns each selected task to the lowest-numbered free worker. Zero-duration tasks complete without using a worker.

## Exit codes

- `0`: success.
- `1`: plan problems.
- `2`: usage or input error.

## Development

Run the test suite and verification with:

```text
mvn -B -q verify
```


## License

MIT, see [LICENSE](LICENSE).

---

Built with BAKED, an open-source coding agent (bench loop round 24, BAKED 7530c78b).
