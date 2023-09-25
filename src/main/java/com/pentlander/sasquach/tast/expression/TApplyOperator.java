package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

public record TApplyOperator(TypedExpression functionCall, Range range) implements TypedExpression {
  @Override
  public Type type() {
    return functionCall.type();
  }
}
