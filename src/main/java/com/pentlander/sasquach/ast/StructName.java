package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.StructName.UnnamedStruct;

public sealed interface StructName extends Name permits QualifiedModuleName, QualifiedTypeName,
    UnnamedStruct, UnqualifiedTypeName {
  record UnnamedStruct(String className) implements StructName {
    @Override
    public String toString() {
      return className;
    }
  }
}
