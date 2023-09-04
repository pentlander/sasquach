package com.pentlander.sasquach;

/**
 * Position in the source code.
 *
 * @param line   the line in source code starting at one.
 * @param column the column the in source code.
 */
public record Position(int line, int column) {
  @Override
  public String toString() {
    return "Position(" + line + ", " + column + ")";
  }
}
