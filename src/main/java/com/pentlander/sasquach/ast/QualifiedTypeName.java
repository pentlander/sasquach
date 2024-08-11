package com.pentlander.sasquach.ast;

public record QualifiedTypeName(QualifiedModuleName qualifiedModuleName, UnqualifiedTypeName name) implements Name,
    StructName, QualifiedName {
  @Override
  public String toString() {
    return qualifiedModuleName.toString() + "$" + name;
  }
}
