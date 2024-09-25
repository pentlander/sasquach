package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.id.TypeIdentifier.UnresolvedTypeName;

public record ModuleScopedTypeName(UnqualifiedName moduleName, UnqualifiedTypeName name) implements
    Name, UnresolvedTypeName {
  @Override
  public String toString() {
    return moduleName + "." + name;
  }
}
