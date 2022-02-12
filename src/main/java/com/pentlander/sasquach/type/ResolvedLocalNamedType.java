package com.pentlander.sasquach.type;

public record ResolvedLocalNamedType(String name, Type type) implements ResolvedNamedType {
  @Override
  public String typeName() {
    return name;
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    return type.isAssignableFrom(other);
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
}
