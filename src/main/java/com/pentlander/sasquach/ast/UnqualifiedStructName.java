package com.pentlander.sasquach.ast;

public record UnqualifiedStructName(String value) implements Name, StructName {
  public UnqualifiedStructName {
    if (value.contains("/") || value.contains("$")) {
      throw new IllegalStateException(
          "Unqualified name cannot contain package separators or nested qualifiers: " + value);
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
