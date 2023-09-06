package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import java.util.List;
import java.util.stream.Collectors;

public record Block(List<Expression> expressions, Range range) implements Expression {

  public Expression returnExpression() {
    var size = expressions.size();
    if (size < 1) {
      throw new IllegalStateException("Block must contain at least one expression.");
    }
    return expressions.get(size - 1);
  }

  public String toPrettyString() {
    return expressions.stream()
        .map(Expression::toPrettyString)
        .collect(Collectors.joining("\n  ", "{\n  ", "\n}"));
  }
}
