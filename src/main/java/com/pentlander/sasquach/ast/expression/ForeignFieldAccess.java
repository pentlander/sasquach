package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.TypeId;

public record ForeignFieldAccess(TypeId classAlias, Id id) implements Expression {

  public String fieldName() {
    return id.name().toString();
  }

  @Override
  public Range range() {
    return classAlias.range().join(id.range());
  }

  @Override
  public String toPrettyString() {
    return classAlias.name() + "#" + id.name();
  }
}
