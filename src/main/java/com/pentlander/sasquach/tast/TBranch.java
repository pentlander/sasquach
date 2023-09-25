package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.Type;

public record TBranch(TPattern pattern, TypedExpression expr, Range range) implements TypedNode {
  @Override
  public Type type() {
    return expr.type();
  }
}
