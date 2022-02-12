package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.Expression;

public record ForeignFieldAccess(Identifier classAlias, Identifier id) implements Expression {

  public String fieldName() {
    return id.name();
  }

  @Override
  public Range range() {
    return classAlias.range().join(id.range());
  }

  @Override
  public String toPrettyString() {
    return classAlias.name() + "#" + id.name();
  }
}
