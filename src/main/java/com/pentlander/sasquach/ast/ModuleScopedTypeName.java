package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.TypeIdentifier.UnresolvedTypeName;

public record ModuleScopedTypeName(UnqualifiedName moduleName, UnqualifiedTypeName name) implements
    Name, UnresolvedTypeName {
  @Override
  public String toString() {
    return moduleName + "." + name;
  }
}
