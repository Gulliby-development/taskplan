package io.github.gulliby.taskplan;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Coordinates argument parsing, input loading, validation, and command reports. */
public final class Cli {
    private Cli() {
    }

    /**
     * Runs taskplan without taking ownership of any supplied stream.
     *
     * @return 0 on success, 1 for plan problems, or 2 for usage/input errors
     */
    public static int run(String[] args, InputStream stdin, PrintStream stdout, PrintStream stderr) {
        Objects.requireNonNull(stdin, "stdin");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");

        final Options options;
        try {
            options = Options.parse(args);
        } catch (PlanException exception) {
            writeLine(stderr, "taskplan: " + exception.getMessage());
            return 2;
        }

        if (options.help()) {
            for (String line : Report.usage()) {
                writeLine(stdout, line);
            }
            return 0;
        }

        String display = options.file().equals("-") ? "<stdin>" : displayPath(options.file());
        final byte[] bytes;
        try {
            bytes = options.file().equals("-")
                    ? stdin.readAllBytes()
                    : Files.readAllBytes(Path.of(options.file()));
        } catch (IOException | InvalidPathException | SecurityException exception) {
            writeLine(stderr, "taskplan: " + display + ": cannot read the file");
            return 2;
        }

        final String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            writeLine(stderr, "taskplan: " + display + ": input is not valid UTF-8");
            return 2;
        }

        final List<Task> tasks;
        try {
            tasks = PlanParser.parse(text, display);
        } catch (PlanException exception) {
            writeLine(stderr, "taskplan: " + exception.getMessage());
            return 2;
        }

        Graph graph = new Graph(tasks);
        if (options.command().equals("check")) {
            List<String> lines = Report.check(display, graph);
            for (String line : lines) {
                writeLine(stdout, line);
            }
            return graph.problems().isEmpty() ? 0 : 1;
        }

        if (graph.hasBlockingProblems()) {
            for (String line : Report.problems(display, graph.blockingProblems())) {
                writeLine(stderr, "taskplan: " + line);
            }
            return 1;
        }

        Cpm cpm = new Cpm(graph);
        return switch (options.command()) {
            case "critical" -> {
                for (String line : Report.critical(cpm)) {
                    writeLine(stdout, line);
                }
                yield 0;
            }
            case "schedule" -> {
                Scheduler scheduler = new Scheduler(cpm, options.workers());
                for (String line : Report.schedule(scheduler)) {
                    writeLine(stdout, line);
                }
                yield 0;
            }
            case "gantt" -> {
                try {
                    for (String line : Report.gantt(cpm, options.scale())) {
                        writeLine(stdout, line);
                    }
                    yield 0;
                } catch (PlanException exception) {
                    writeLine(stderr, "taskplan: " + exception.getMessage());
                    yield 2;
                }
            }
            default -> throw new IllegalStateException("unrecognized parsed command");
        };
    }

    private static String displayPath(String file) {
        return file.replace('\\', '/');
    }

    private static void writeLine(PrintStream stream, String line) {
        stream.print(line);
        stream.print('\n');
    }
}
