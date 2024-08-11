package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.TypeId;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Objects;

/**
 * Unqualified named type. It can refer to a local type alias or a type parameter.
 */
public record LocalNamedType(TypeId id, List<TypeNode> typeArgumentNodes) implements NamedType {
  public LocalNamedType {
    Objects.requireNonNull(typeArgumentNodes);
  }

  public LocalNamedType(TypeId id) {
    this(id, List.of());
  }

  @Override
  public String typeNameStr() {
    return id.name().toString();
  }

  @Override
  public UnqualifiedTypeName typeName() {
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
