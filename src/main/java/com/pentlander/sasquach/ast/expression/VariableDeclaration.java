package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.Type;
import org.jspecify.annotations.Nullable;

public record VariableDeclaration(Id id, @Nullable TypeNode typeAnnotation, Expression expression, Range range) implements Expression, LocalVariable {
  public VariableDeclaration(Id id, Expression expression, Range range) {
    this(id, null, expression, range);
  }

  public UnqualifiedName name() {
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
