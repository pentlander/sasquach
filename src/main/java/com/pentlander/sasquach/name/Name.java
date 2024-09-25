package com.pentlander.sasquach.name;

public interface Name {
  String toString();

  default String toPrettyString() {
    return toString();
  }

  static void requireUnqualified(String name) {
    if (name.contains("/") || name.contains("$")) {
      throw new IllegalStateException(
          "Unqualified name cannot contain package separators or nested qualifiers: " + name);
    }
  }
}
