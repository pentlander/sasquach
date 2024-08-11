package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.TypeId;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.ForeignFieldType;

public record TForeignFieldAccess(TypeId classAlias, Id id,
                                  ForeignFieldType type) implements TypedExpression {

  public UnqualifiedName fieldName() {
    return id.name();
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
