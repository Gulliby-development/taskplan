package io.github.gulliby.taskplan;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** UTF-8 process entry point for taskplan. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        PrintStream stdout = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream stderr = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        int exitCode = Cli.run(args, System.in, stdout, stderr);
        stdout.flush();
        stderr.flush();
        System.exit(exitCode);
    }
}
