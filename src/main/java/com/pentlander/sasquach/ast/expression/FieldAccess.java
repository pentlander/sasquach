package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;

public record FieldAccess(Expression expr, Id id) implements Expression {
  public static FieldAccess of(Expression expr, String fieldName, Range.Single range) {
    return new FieldAccess(expr, new Id(fieldName, range));
  }

  public String fieldName() {
    return id.name();
  }

  @Override
  public Range range() {
    return expr.range().join(id.range());
  }
}
