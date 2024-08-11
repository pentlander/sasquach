package com.pentlander.sasquach.ast;

public interface Name {
  String toString();

  static void requireUnqualified(String name) {
    if (name.contains("/") || name.contains("$")) {
      throw new IllegalStateException(
          "Unqualified name cannot contain package separators or nested qualifiers: " + name);
    }
  }
}
