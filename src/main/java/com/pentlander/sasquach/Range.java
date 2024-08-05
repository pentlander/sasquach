package com.pentlander.sasquach;

/**
 * Range of text in source code.
 */
public sealed interface Range {
  SourcePath sourcePath();

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
    if (!sourcePath().equals(other.sourcePath())) {
      throw new IllegalArgumentException(("Cannot join range from source file '%s' with range "
          + "from '%s'").formatted(sourcePath(), other.sourcePath()));
    }

    var first =
        start().line() <= other.start().line() && start().column() <= other.start().column() ? this
            : other;
    var second = first.equals(this) ? other : this;
    if (start().line() == other.start().line() && other instanceof Single) {
      return new Single(sourcePath(),
          first.start(),
          second.end().column() - first.start().column());
    }
    return new Multi(sourcePath(), first.start(), second.end());
  }

  /**
   * Range that starts on one line and ends on another.
   */
  record Multi(SourcePath sourcePath, Position start, Position end) implements Range {}

  /**
   * Range that starts and ends on the same line.
   */
  record Single(SourcePath sourcePath, Position start, int length) implements Range {
    public Position end() {
      return new Position(start.line(), start.column() + length);
    }

    public Range.Single join(Range.Single other) {
      return new Single(sourcePath, start, other.end().column() + start().column());
    }

    @Override
    public String toString() {
      return sourcePath.toString() + ":" + start.line() + ":" + start.column() + "-" + (
          start.column() + length);
    }
  }
}
