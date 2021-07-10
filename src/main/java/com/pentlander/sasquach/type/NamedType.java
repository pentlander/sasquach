package com.pentlander.sasquach.type;

/**
 * Late binding type that is only known to the TypeResolver.
 */
public record NamedType(String name) implements ParameterizedType {
  @Override
  public String typeName() {
    return name;
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    return true;
  }

  @Override
  public Class<?> typeClass() {
    throw new IllegalStateException();
  }

  @Override
  public String descriptor() {
    return "Ljava/lang/Object;";
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }
}
