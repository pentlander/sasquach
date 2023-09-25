package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.tast.TPatternVariable;

public sealed interface TLocalVariable extends TypedExpression permits TPatternVariable,
    TVariableDeclaration {
  Identifier id();

  default String name() {
    return id().name();
  }
}
