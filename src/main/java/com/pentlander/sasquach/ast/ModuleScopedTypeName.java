package com.pentlander.sasquach.ast;

public record ModuleScopedTypeName(UnqualifiedName moduleName, UnqualifiedTypeName name) implements
    Name {
  @Override
  public String toString() {
    return moduleName + "." + name;
  }
}
