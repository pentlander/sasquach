package com.pentlander.sasquach.name;

public record UnqualifiedTypeName(String value) implements Name, StructName, TypeName {
  public UnqualifiedTypeName {
    Name.requireUnqualified(value);
  }

  @Override
  public UnqualifiedTypeName simpleName() {
    return this;
  }

  @Override
  public String toString() {
    return value;
  }

  public UnqualifiedName toName() {
    return new UnqualifiedName(value);
  }
}
