package com.pentlander.sasquach.type;

public record ResolvedModuleNamedType(String moduleName, String name, Type type) implements ResolvedNamedType {
  @Override
  public String typeName() {
    return moduleName + "." + name;
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
