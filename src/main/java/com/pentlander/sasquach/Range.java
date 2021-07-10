package com.pentlander.sasquach;

/**
 * Range of text in source code.
 */
public interface Range {
  /**
   * Starting source code position.
   */
  Position start();

  /**
   * Ending source code position.
   */
  Position end();

  /**
   * Join two ranges to create a spanning range.
   *
   * @param other other range to join with.
   * @return a range that begins at the start of this range and finishes at the end of the other
   * range.
   */
  default Range join(Range other) {
    var first =
        start().line() <= other.start().line() && start().column() <= other.start().column() ? this
            : other;
    var second = first.equals(this) ? other : this;
    if (start().line() == other.start().line() && other instanceof Single) {
      return new Single(first.start(), second.end().column() - first.start().column());
    }
    return new Multi(first.start(), second.end());
  }

  /**
   * Range that starts on one line and ends on another.
   */
  record Multi(Position start, Position end) implements Range {}

  /**
   * Range that starts and ends on the same line.
   */
  record Single(Position start, int length) implements Range {
    public Position end() {
      return new Position(start.line(), start.column() + length);
    }
  }
}
