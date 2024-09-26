package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.id.TypeIdentifier;
import com.pentlander.sasquach.name.TypeName;
import java.lang.constant.ClassDesc;
import java.util.List;

/**
 * Unresolved type referred to by captureName.
 **/
// TODO change id to a name
public record NamedType(TypeIdentifier id, List<Type> typeArguments) implements Type {
  TypeName typeName() {
    return id().name();
  }

  @Override
  public String typeNameStr() {
    return id.name().toString();
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }

  @Override
  public ClassDesc classDesc() {
    throw new IllegalStateException();
  }
}

