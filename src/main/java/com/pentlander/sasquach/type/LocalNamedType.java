package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.Identifier;

/**
 * Late binding type that is only known to the TypeResolver.
 */
public record LocalNamedType(Identifier id) implements NamedType {
  @Override
  public String typeName() {
    return id.name();
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
