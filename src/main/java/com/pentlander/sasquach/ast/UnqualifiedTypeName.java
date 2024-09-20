package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.TypeIdentifier.UnresolvedTypeName;

public record UnqualifiedTypeName(String value) implements Name, StructName, UnresolvedTypeName {
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
