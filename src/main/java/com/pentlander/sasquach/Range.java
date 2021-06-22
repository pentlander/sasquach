package com.pentlander.sasquach;

public interface Range {
  Position start();
  Position end();

  default Range join(Range other) {
    var first = start().line() <= other.start().line() && start().column() <= other.start().column() ? this : other;
    var second = first.equals(this) ? other : this;
    if (start().line() == other.start().line() && other instanceof Single) {
      return new Single(first.start(), second.end().column() - first.start().column());
    }
    return new Multi(first.start(), second.end());
  }

  record Multi(Position start, Position end) implements Range {
  }

  record Single(Position start, int length) implements Range {
    public Position end() {
      return new Position(start.line(), start.column() + length);
    }
  }
}
