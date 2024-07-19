package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;

public record TNot(TypedExpression expr, Range range) implements TypedExpression{
  @Override
  public Type type() {
    return BuiltinType.BOOLEAN;
  }
}
