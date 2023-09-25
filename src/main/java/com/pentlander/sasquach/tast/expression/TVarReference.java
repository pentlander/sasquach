package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.type.Type;

public record TVarReference(Identifier id, Type type) implements TypedExpression {

  public String name() {
    return id.name();
  }

  @Override
  public Range range() {
    return id.range();
  }

  @Override
  public String toPrettyString() {
    return id().name();
  }
}
