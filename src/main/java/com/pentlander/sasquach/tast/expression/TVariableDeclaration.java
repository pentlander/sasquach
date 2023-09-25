package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;

public record TVariableDeclaration(Identifier id, TypedExpression expression,
                                   Range range) implements TLocalVariable {
  public String name() {
    return id.name();
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
