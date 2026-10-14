package io.github.gulliby.taskplan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Parses the command-line arguments for taskplan. */
public record Options(String command, String file, int workers, int scale, boolean help) {
    public static final String USAGE = String.join("\n",
            "usage: taskplan check FILE",
            "       taskplan critical FILE",
            "       taskplan schedule [--workers N] FILE",
            "       taskplan gantt [--scale K] FILE");

    public Options {
        Objects.requireNonNull(command, "command");
        if (!help) {
            Objects.requireNonNull(file, "file");
        }
    }

    /** Parses arguments and throws a user-facing error when they are invalid. */
    public static Options parse(String[] args) {
        Objects.requireNonNull(args, "args");
        if (args.length == 0) {
            throw new PlanException("missing command (check, critical, schedule or gantt)");
        }
        if (args.length == 1 && args[0].equals("--help")) {
            return new Options("", null, 2, 1, true);
        }
        if (args[0].equals("--help")) {
            throw unknownOption(args[0]);
        }

        String command = args[0];
        if (!command.equals("check") && !command.equals("critical")
                && !command.equals("schedule") && !command.equals("gantt")) {
            throw new PlanException("unknown command \"" + command + "\"");
        }

        String workerText = null;
        String scaleText = null;
        List<String> positionals = new ArrayList<>();
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if (argument.equals("--workers")) {
                if (index + 1 == args.length) {
                    throw new PlanException("--workers needs a value");
                }
                workerText = args[++index];
            } else if (argument.startsWith("--workers=")) {
                workerText = argument.substring("--workers=".length());
            } else if (argument.equals("--scale")) {
                if (index + 1 == args.length) {
                    throw new PlanException("--scale needs a value");
                }
                scaleText = args[++index];
            } else if (argument.startsWith("--scale=")) {
                scaleText = argument.substring("--scale=".length());
            } else if (argument.startsWith("-") && !argument.equals("-")) {
                throw unknownOption(argument);
            } else {
                positionals.add(argument);
            }
        }

        int workers = workerText == null ? 2 : parseNumber(
                "--workers", workerText, 1, 64, "a whole number from 1 to 64");
        int scale = scaleText == null ? 1 : parseNumber(
                "--scale", scaleText, 1, 1000, "a whole number from 1 to 1000");
        if (workerText != null && !command.equals("schedule")) {
            throw new PlanException("--workers is only valid for schedule");
        }
        if (scaleText != null && !command.equals("gantt")) {
            throw new PlanException("--scale is only valid for gantt");
        }
        if (positionals.isEmpty()) {
            throw new PlanException("missing FILE");
        }
        if (positionals.size() > 1) {
            throw new PlanException("too many arguments");
        }
        return new Options(command, positionals.get(0), workers, scale, false);
    }

    public boolean isHelp() {
        return help;
    }

    private static int parseNumber(String option, String value, int minimum, int maximum, String expected) {
        if (!isCanonicalPositiveInteger(value)) {
            throw invalidNumber(option, value, expected);
        }
        int parsed = 0;
        for (int index = 0; index < value.length(); index++) {
            int digit = value.charAt(index) - '0';
            if (parsed > maximum / 10 || (parsed == maximum / 10 && digit > maximum % 10)) {
                throw invalidNumber(option, value, expected);
            }
            parsed = parsed * 10 + digit;
        }
        if (parsed < minimum || parsed > maximum) {
            throw invalidNumber(option, value, expected);
        }
        return parsed;
    }

    private static boolean isCanonicalPositiveInteger(String value) {
        if (value.isEmpty() || (value.length() > 1 && value.charAt(0) == '0')) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static PlanException invalidNumber(String option, String value, String expected) {
        return new PlanException("invalid " + option + " \"" + value + "\" (expected " + expected + ")");
    }

    private static PlanException unknownOption(String argument) {
        return new PlanException("unknown option \"" + argument + "\"");
    }
}
