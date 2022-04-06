package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleName;

public record VariantStructType(QualifiedModuleName moduleName, StructType structType) implements VariantType {
  @Override
  public String typeName() {
    return structType.typeName();
  }

  @Override
  public Class<?> typeClass() {
    return StructType.class;
  }

  @Override
  public String descriptor() {
    return structType.descriptor();
  }

  @Override
  public String internalName() {
    return structType.internalName();
  }
}
