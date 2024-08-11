package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.ModuleScopedTypeId;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import java.lang.constant.ClassDesc;
import java.util.List;

/**
 * Module qualified named type. It can only refer to a type alias defined in another module.
 *
 * <p> Example: <code>Map.T</code> </p>
 */
public record ModuleNamedType(ModuleScopedTypeId id,
                              List<TypeNode> typeArgumentNodes) implements NamedType {

  public UnqualifiedTypeName name() {
    return id.id().name();
  }

  public UnqualifiedName moduleName() {
    return id.moduleId().name();
  }

  @Override
  public String typeNameStr() {
    return id.name().toString();
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
