package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.PatternVariable;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.nameres.MemberScopedNameResolver.FunctionCallTarget;

public sealed interface LocalVariable extends Node, FunctionCallTarget permits FunctionParameter,
    VariableDeclaration, PatternVariable {
  Id id();

  default UnqualifiedName name() {
    return id().name();
  }
}
