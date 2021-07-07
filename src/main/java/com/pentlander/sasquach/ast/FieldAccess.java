package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record FieldAccess(Expression expr, Identifier id) implements Expression {
  public static FieldAccess of(Expression expr, String fieldName, Range.Single range) {
    return new FieldAccess(expr, new Identifier(fieldName, range));
  }

  public String fieldName() {
    return id.name();
  }

  @Override
  public Range range() {
    return expr.range().join(id.range());
  }
}
