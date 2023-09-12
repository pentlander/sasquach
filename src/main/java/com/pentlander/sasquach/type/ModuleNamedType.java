package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.ModuleScopedIdentifier;
import com.pentlander.sasquach.ast.TypeNode;
import java.lang.constant.ClassDesc;
import java.util.List;

/**
 * Module qualified named type. It can only refer to a type alias defined in another module.
 */
public record ModuleNamedType(ModuleScopedIdentifier id,
                              List<TypeNode> typeArgumentNodes) implements NamedType {
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
  public ClassDesc classDesc() {
    throw new IllegalStateException();
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }
}
