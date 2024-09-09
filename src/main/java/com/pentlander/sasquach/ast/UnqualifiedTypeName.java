package com.pentlander.sasquach.ast;

public record UnqualifiedTypeName(String value) implements Name, StructName {
  public UnqualifiedTypeName {
    Name.requireUnqualified(value);
  }

  @Override
  public String toString() {
    return value;
  }

  public UnqualifiedName toName() {
    return new UnqualifiedName(value);
  }
}
