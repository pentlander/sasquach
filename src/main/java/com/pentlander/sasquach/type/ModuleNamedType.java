package com.pentlander.sasquach.type;

public record ModuleNamedType(String moduleName, String name) implements NamedType {
  @Override
  public String typeName() {
    return moduleName + "." + name;
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
    throw new IllegalStateException();
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }
}
