package com.pentlander.sasquach;

import static com.pentlander.sasquach.Source.DecorationChar.*;

import java.util.Arrays;
import java.util.List;

/**
 * Class containing the source code as a string.
 * <p>It has convenience methods for highlighting parts of the source code.</p>
 */
public record Source(SourcePath path, String packageName, List<String> sourceLines) {
  public static Source fromString(String packageName, String source) {
    return new Source(new SourcePath("anon.sasq"), packageName, Arrays.asList(source.split("\n")));
  }

  public String sourceString() {
    return String.join("\n", sourceLines());
  }

  static String underline(Range.Single range, int offset) {
    return " ".repeat(range.start().column() + offset) + "^".repeat(range.length());
  }

  public String highlight(Range range) {
    return switch (range) {
      case Range.Single single ->
          getNumberedLines(single) + '\n' + leftPad("") + " " + LBOT.unicode() + underline(single,0);
      case Range.Multi multi -> getNumberedLines(multi);
    };
  }

  private int lineNumberWidth() {
    return String.valueOf(sourceLines.size()).length();
  }

  private String leftPad(String value) {
    return " ".repeat(lineNumberWidth() - value.length()) + value;
  }

  private String lineNumber(int lineNumber) {
    return leftPad(String.valueOf(lineNumber)) + " " + VBAR.unicode();
  }

  public String getNumberedLines(Range range) {
    Position start = range.start();
    return switch (range) {
      case Range.Single _ -> lineNumber(start.line()) + sourceLines.get(start.line() - 1);
      case Range.Multi multiRange -> {
        Position end = multiRange.end();
        var builder = new StringBuilder();
        for (int i = start.line() - 1; i < end.line(); i++) {
          builder.append(lineNumber(i)).append(sourceLines.get(i)).append('\n');
        }
        yield builder.toString();
      }
    };
  }

  enum DecorationChar {
    HBAR("─"),
    VBAR("│"),
    UARROW("▲"),
    RARROW("▶"),
    LTOP("╭"),
    RTOP("╮"),
    LBOT("╰"),
    RBOT("╯"),
    UNDERLINE("─");

    private final String unicode;

    DecorationChar(String unicode) {
      this.unicode = unicode;
    }

    public String unicode() {
      return unicode;
    }
  }
}
