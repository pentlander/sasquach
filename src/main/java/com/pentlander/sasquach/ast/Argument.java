package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Expression;
import org.jspecify.annotations.Nullable;

public record Argument(@Nullable UnqualifiedName label, Expression expression, Range range) implements Node, Labeled {
  public Argument(Expression expression) {
    this(null, expression, expression.range());
  }
}
