package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.Expression;

public record VariableDeclaration(Identifier id, Expression expression, int index,
                                  Range range) implements Expression, LocalVariable {
  public String name() {
    return id.name();
  }

  public Range.Single nameRange() {
    return id.range();
  }
}
