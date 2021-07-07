package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record ForeignFieldAccess(Identifier classAlias, Identifier id) implements Expression {

  public String fieldName() {
    return id.name();
  }

  @Override
  public Range range() {
    return classAlias.range().join(id.range());
  }
}
