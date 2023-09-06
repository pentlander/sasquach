package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.type.Type;

public record TypedExprWrapper(Expression expr, Type type) implements TypedExpression {
  @Override
  public Range range() {
    return expr.range();
  }

  @Override
  public String toPrettyString() {
    return expr.toPrettyString();
  }
}
