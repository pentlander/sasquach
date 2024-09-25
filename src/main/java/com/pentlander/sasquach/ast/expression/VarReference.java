package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.name.UnqualifiedName;

public record VarReference(Id id) implements Expression {

  public UnqualifiedName name() {
    return id.name();
  }

  @Override
  public Range range() {
    return id.range();
  }

  @Override
  public String toPrettyString() {
    return id().name().toString();
  }
}
