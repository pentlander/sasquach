package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleName;

public record SingletonType(QualifiedModuleName moduleName, String name) implements VariantType {
  @Override
  public String typeName() {
    return name;
  }

  @Override
  public Class<?> typeClass() {
    return null;
  }

  @Override
  public String descriptor() {
    return null;
  }

  @Override
  public String internalName() {
    return null;
  }
}
