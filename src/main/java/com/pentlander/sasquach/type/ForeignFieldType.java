package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;

/**
 * Type a foreign field access.
 */
public record ForeignFieldType(Type type, Type ownerType, FieldAccessKind accessKind) implements
    Type {
  @Override
  public String typeName() {
    return type.typeName();
  }

  @Override
  public ClassDesc classDesc() {
    return type.classDesc();
  }

  @Override
  public String internalName() {
    return type.internalName();
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    return type.isAssignableFrom(other);
  }
}
