package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;

public record VariableDeclaration(Id id, Expression expression, Range range) implements Expression, LocalVariable {
  public String name() {
    return id.name();
  }

  public Range.Single nameRange() {
    return id.range();
  }

  @Override
  public String toPrettyString() {
    return "%s = %s".formatted(id.name(), expression.toPrettyString());
  }
}
