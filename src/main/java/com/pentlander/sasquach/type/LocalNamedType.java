package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.TypeNode;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Objects;

/**
 * Unqualified named type. It can refer to a local type alias or a type parameter.
 */
public record LocalNamedType(Id id, List<TypeNode> typeArgumentNodes) implements NamedType {
  public LocalNamedType {
    Objects.requireNonNull(typeArgumentNodes);
  }

  public LocalNamedType(Id id) {
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
  public ClassDesc classDesc() {
    throw new IllegalStateException(toString());
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }
}
