package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;

public record VarReference(Id id) implements Expression {
  public static VarReference of(String name, Range.Single range) {
    return new VarReference(new Id(name, range));
  }

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
