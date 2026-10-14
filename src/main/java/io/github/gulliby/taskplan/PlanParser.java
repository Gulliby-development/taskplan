package io.github.gulliby.taskplan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses the line-oriented taskplan file format. */
public final class PlanParser {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,40}");

    private PlanParser() {
    }

    /**
     * Parses a plan and prefixes input errors with the supplied display name.
     *
     * @param text plan contents
     * @param display path or display name used in diagnostics
     * @return tasks in file order
     * @throws PlanException when a line is invalid or the plan contains no tasks
     */
    public static List<Task> parse(String text, String display) {
        if (text == null) {
            throw new NullPointerException("text");
        }
        if (display == null) {
            throw new NullPointerException("display");
        }

        String source = text.startsWith("\uFEFF") ? text.substring(1) : text;
        String[] lines = source.split("\n", -1);
        List<Task> tasks = new ArrayList<>();

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (index < lines.length - 1 && line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            String content = trimSpacesAndTabs(line);
            if (content.isEmpty() || content.charAt(0) == '#') {
                continue;
            }

            int colon = content.indexOf(':');
            if (colon < 0) {
                throw lineError(display, index + 1, "expected \"NAME: DURATION [after NAME...]\"");
            }

            String name = content.substring(0, colon);
            if (!isName(name)) {
                throw lineError(display, index + 1, "invalid task name \"" + printable(name) + "\"");
            }

            String remainder = content.substring(colon + 1);
            int durationStart = skipSpacesAndTabs(remainder, 0);
            if (durationStart == remainder.length()) {
                throw lineError(display, index + 1, "missing duration");
            }
            int durationEnd = endOfWord(remainder, durationStart);
            String durationText = remainder.substring(durationStart, durationEnd);
            if (!isDecimalDuration(durationText)) {
                throw invalidDuration(display, index + 1, durationText);
            }
            int duration = Integer.parseInt(durationText);
            if (duration > 100000) {
                throw invalidDuration(display, index + 1, durationText);
            }

            int next = skipSpacesAndTabs(remainder, durationEnd);
            Set<String> dependencies = new LinkedHashSet<>();
            if (next < remainder.length()) {
                int wordEnd = endOfWord(remainder, next);
                String keyword = remainder.substring(next, wordEnd);
                if (!keyword.equals("after")) {
                    throw lineError(display, index + 1,
                            "expected \"after\" or end of line, found \"" + printable(keyword) + "\"");
                }

                next = skipSpacesAndTabs(remainder, wordEnd);
                if (next == remainder.length()) {
                    throw lineError(display, index + 1, "\"after\" needs at least one task name");
                }

                while (next < remainder.length()) {
                    int dependencyEnd = endOfWord(remainder, next);
                    String dependency = remainder.substring(next, dependencyEnd);
                    if (!isName(dependency)) {
                        throw lineError(display, index + 1,
                                "invalid dependency name \"" + printable(dependency) + "\"");
                    }
                    dependencies.add(dependency);
                    next = skipSpacesAndTabs(remainder, dependencyEnd);
                }
            }

            tasks.add(new Task(name, duration, List.copyOf(dependencies), index + 1));
        }

        if (tasks.isEmpty()) {
            throw new PlanException(display + ": no tasks");
        }
        return List.copyOf(tasks);
    }

    private static PlanException invalidDuration(String display, int line, String value) {
        return lineError(display, line,
                "invalid duration \"" + printable(value) + "\" (expected a whole number from 0 to 100000)");
    }

    private static PlanException lineError(String display, int line, String message) {
        return new PlanException(display + ":" + line + ": " + message);
    }

    private static boolean isName(String value) {
        return NAME.matcher(value).matches();
    }

    private static boolean isDecimalDuration(String value) {
        if (value.isEmpty() || value.length() > 6) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static int skipSpacesAndTabs(String value, int index) {
        while (index < value.length()) {
            char character = value.charAt(index);
            if (character != ' ' && character != '\t') {
                break;
            }
            index++;
        }
        return index;
    }

    private static int endOfWord(String value, int start) {
        int index = start;
        while (index < value.length()) {
            char character = value.charAt(index);
            if (character == ' ' || character == '\t') {
                break;
            }
            index++;
        }
        return index;
    }

    private static String trimSpacesAndTabs(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpaceOrTab(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpaceOrTab(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isSpaceOrTab(char character) {
        return character == ' ' || character == '\t';
    }

    private static String printable(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (codePoint < 0x20 || codePoint > 0x7e) {
                result.append('?');
            } else {
                result.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
