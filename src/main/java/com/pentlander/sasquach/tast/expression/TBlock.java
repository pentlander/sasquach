package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TBlock(List<? extends TypedExpression> expressions, Range range) implements
    TypedExpression {

  public TypedExpression returnExpression() {
    var size = expressions.size();
    if (size < 1) {
      throw new IllegalStateException("Block must contain at least one expression.");
    }
    return expressions.get(size - 1);
  }

  @Override
  public Type type() {
    return returnExpression().type();
  }
}
