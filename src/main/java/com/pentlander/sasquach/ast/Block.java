package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import java.util.List;

public record Block(Scope scope, List<Expression> expressions, Range range) implements Expression {

  public Expression returnExpression() {
    var size = expressions.size();
    if (size < 1) {
      throw new IllegalStateException("Block must contain at least one expression.");
    }
    return expressions.get(size - 1);
  }
}
