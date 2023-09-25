package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;

public record TPrintStatement(TypedExpression expression, Range range) implements TypedExpression {
  @Override
  public Type type() {
    return BuiltinType.VOID;
  }
}
