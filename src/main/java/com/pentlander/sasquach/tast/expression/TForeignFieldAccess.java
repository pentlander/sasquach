package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.FieldAccessKind;
import com.pentlander.sasquach.type.Type;

public record TForeignFieldAccess(TypeId classAlias, Id id,
                                  ClassType ownerType,
                                  Type type,
                                  FieldAccessKind accessKind) implements TypedExpression {

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
