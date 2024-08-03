package com.pentlander.sasquach.ast;

public record QualifiedStructName(QualifiedModuleName qualifiedModuleName, UnqualifiedStructName name) implements Name,
    StructName, QualifiedName {
  @Override
  public String toString() {
    return qualifiedModuleName.toString() + "$" + name;
  }
}
