package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.id.TypeIdentifier.UnresolvedTypeName;

public record UnqualifiedTypeName(String value) implements Name, StructName, UnresolvedTypeName {
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