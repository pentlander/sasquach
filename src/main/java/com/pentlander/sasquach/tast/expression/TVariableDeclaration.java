package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;

public record TVariableDeclaration(Id id, TypedExpression expression,
                                   Range range) implements TypedExpression, TLocalVariable {
  public UnqualifiedName name() {
    return id.name();
  }

  @Override
  public Type variableType() {
    return expression.type();
  }

  public Range.Single nameRange() {
    return id.range();
  }

  @Override
  public Type type() {
    return BuiltinType.VOID;
  }

  @Override
  public String toPrettyString() {
    return "%s = %s".formatted(id.name(), expression.toPrettyString());
  }
}
