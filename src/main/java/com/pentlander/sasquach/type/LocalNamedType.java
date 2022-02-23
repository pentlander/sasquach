package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.TypeNode;
import java.util.List;
import java.util.Objects;

/**
 * Unqualified named type. It can refer to a local type alias or a type parameter.
 */
public record LocalNamedType(Identifier id, List<TypeNode<Type>> typeArgumentNodes) implements NamedType {
  public LocalNamedType {
    Objects.requireNonNull(typeArgumentNodes);
  }

  public LocalNamedType(Identifier id) {
    this(id, List.of());
  }

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
    throw new IllegalStateException(toString());
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }
}
