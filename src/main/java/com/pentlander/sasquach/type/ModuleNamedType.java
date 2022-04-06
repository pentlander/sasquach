package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.ModuleScopedIdentifier;
import com.pentlander.sasquach.ast.TypeNode;
import java.util.List;

/**
 * Module qualified named type. It can only refer to a type alias defined in another module.
 */
public record ModuleNamedType(ModuleScopedIdentifier id, List<TypeNode> typeArgumentNodes) implements NamedType {
  public String name() {
    return id.id().name();
  }

  public String moduleName() {
    return id.moduleId().name();
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
    throw new IllegalStateException();
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }
}
