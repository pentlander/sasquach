package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.type.Type;

public record TFieldAccess(TypedExpression expr, Id id, Type type) implements TypedExpression {

  public String fieldName() {
    return id.name();
  }

  @Override
  public Range range() {
    return expr.range().join(id.range());
  }
}
