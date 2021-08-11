package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;

public sealed interface LocalVariable extends Node permits FunctionParameter, VariableDeclaration {
  Identifier id();

  default String name() {
    return id().name();
  }
}
