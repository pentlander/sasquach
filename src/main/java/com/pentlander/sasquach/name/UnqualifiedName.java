package com.pentlander.sasquach.name;

public record UnqualifiedName(String value) implements Name, Comparable<UnqualifiedName> {
  public static final UnqualifiedName EMPTY = new UnqualifiedName("");

  public UnqualifiedName {
    Name.requireUnqualified(value);
  }

  @Override
  public String toString() {
    return value;
  }

  public UnqualifiedTypeName toTypeName() {
    return new UnqualifiedTypeName(value);
  }

  @Override
  public int compareTo(UnqualifiedName o) {
    return value.compareTo(o.value);
  }
}
