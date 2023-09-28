package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TPatternVariable;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.type.Type;

public sealed interface TLocalVariable extends TypedNode permits FunctionParameter,
    TFunctionParameter, TPatternVariable, TVariableDeclaration {
  Identifier id();
  default Type variableType() {
    return type();
  }

  default String name() {
    return id().name();
  }
}
