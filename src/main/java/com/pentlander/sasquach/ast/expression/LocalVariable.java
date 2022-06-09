package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.PatternVariable;
import com.pentlander.sasquach.name.MemberScopedNameResolver.FunctionCallTarget;

public sealed interface LocalVariable extends Node, FunctionCallTarget permits FunctionParameter,
    VariableDeclaration, PatternVariable, HiddenVariable {
  Identifier id();

  default String name() {
    return id().name();
  }
}
