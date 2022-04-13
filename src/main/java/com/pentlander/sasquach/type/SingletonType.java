package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleName;

public record SingletonType(QualifiedModuleName moduleName, String name) implements VariantType {
  @Override
  public String typeName() {
    return name;
  }

  @Override
  public Class<?> typeClass() {
    throw new IllegalStateException();
  }

  @Override
  public String descriptor() {
    return "L" + moduleName.qualify(name) + ";";
  }

  @Override
  public String internalName() {
    return moduleName.qualify(name);
  }
}
