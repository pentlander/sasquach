package com.pentlander.sasquach.type;

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
  public Class<?> typeClass() {
    return type.typeClass();
  }

  @Override
  public String descriptor() {
    return type.descriptor();
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
