package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;
import java.util.List;

public record ResolvedModuleNamedType(String moduleName, String name, List<Type> typeArgs,
                                      Type type) implements ResolvedNamedType {
  public ResolvedModuleNamedType {
    if (type instanceof NamedType) {
      throw new IllegalStateException("Cannot contain unresolved hamed type: " + type);
    }
  }

  @Override
  public String typeName() {
    return moduleName + "." + name;
  }

  @Override
  public ClassDesc classDesc() {
    return type.classDesc();
  }

  @Override
  public String internalName() {
    return type.internalName();
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    return type.isAssignableFrom(other);
  }

  @Override
  public String toPrettyString() {
    return TypeUtils.typeWithArgsToString(moduleName + "." + name, typeArgs);
  }
}
