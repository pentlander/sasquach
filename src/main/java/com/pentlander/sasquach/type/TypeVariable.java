package com.pentlander.sasquach.type;

/**
 * Represents a type parameter that hasn't been resolved yet. It is essentially a placeholder
 * that gets replaced by the {@link TypeUnifier}.
 * @param name name of the type variable.
 */
public record TypeVariable(String name) implements Type, ParameterizedType {
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
    return Object.class.getCanonicalName().replace('.', '/');
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    return true;
  }
}
