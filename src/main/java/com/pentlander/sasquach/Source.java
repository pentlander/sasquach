package com.pentlander.sasquach;

import java.util.List;

record Source(List<String> sourceLines) {

  static String underline(Range.Single range, int offset) {
    return " ".repeat(range.start().column() + offset) + "^".repeat(range.length());
  }

  String highlight(Range range) {
    if (range instanceof Range.Single single){
      var lineStr = String.valueOf(range.start().line());
      return lineNumber(single.start().line())
          + sourceLines.get(range.start().line() - 1)
          + '\n'
          + underline(single, lineStr.length() + 2);
    } else if (range instanceof Range.Multi multi) {
      return getNumberedLines(multi);
    }
    throw new IllegalStateException();
  }

  private int lineNumberWidth() {
     return String.valueOf(sourceLines.size()).length();
  }

  private String leftPad(String value) {
    return " ".repeat(lineNumberWidth() - value.length()) + value;
  }

  private String lineNumber(int lineNumber) {
    return leftPad(String.valueOf(lineNumber)) + ": ";
  }

  public String getNumberedLines(Range range) {
    Position start = range.start();
    if (range instanceof Range.Single) {
      return lineNumber(start.line()) + sourceLines.get(start.line() - 1);
    } else if (range instanceof Range.Multi multiRange) {
      Position end = multiRange.end();
      var builder = new StringBuilder();
      for (int i = start.line() - 1; i < end.line(); i++) {
        builder
            .append(lineNumber(i))
            .append(sourceLines.get(i))
            .append('\n');
      }
      return builder.toString();
    }
    throw new IllegalStateException();
  }
}
