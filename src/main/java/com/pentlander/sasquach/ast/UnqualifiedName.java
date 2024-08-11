package com.pentlander.sasquach.ast;

public record UnqualifiedName(String value) implements Name, Comparable<UnqualifiedName> {
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
