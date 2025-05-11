package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TPatternVariable;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.type.Type;

public sealed interface TLocalVariable extends TypedNode permits
    TFunctionParameter, TPatternVariable, TVariableDeclaration {
  Id id();
  default Type variableType() {
    return type();
  }

  default UnqualifiedName name() {
    return id().name();
  }
}
