package com.pentlander.sasquach.type;

public record TypeParameter(String name) implements Type, ParameterizedType {
  @Override
  public String typeName() {
    return name;
  }

  @Override
  public Class<?> typeClass() {
    return Object.class;
  }

  @Override
  public String descriptor() {
    return Object.class.descriptorString();
  }

  @Override
  public String internalName() {
    return Object.class.getCanonicalName();
  }
}
