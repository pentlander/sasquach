package com.pentlander.sasquach;

public interface Range {

  Position start();

  record Multi(Position start, Position end) implements Range {
    public int length() {
      if (start.line() != end.line()) {
        throw new IllegalStateException("Start and end must be on the same line");
      }
      return end.column() - start.column();
    }
  }

  record Single(Position start, int length) implements Range {
  }
}
