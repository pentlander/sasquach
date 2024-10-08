package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.name.UnqualifiedName;

public record MemberAccess(Expression expr, Id id) implements Expression {
  public static MemberAccess of(Expression expr, String fieldName, Range.Single range) {
    return new MemberAccess(expr, new Id(fieldName, range));
  }

  public UnqualifiedName fieldName() {
    return id.name();
  }

  @Override
  public Range range() {
    return expr.range().join(id.range());
  }
}
