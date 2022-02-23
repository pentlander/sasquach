package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.Id;

/**
 * Represents a type parameter defined as part of a type alias or function signature.
 */
public record TypeParameter(Id id) implements Type {
  @Override
  public String typeName() {
    return id.name();
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
