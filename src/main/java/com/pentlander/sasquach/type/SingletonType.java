package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedTypeName;
import com.pentlander.sasquach.runtime.StructBase;
import java.lang.constant.ClassDesc;
import java.util.List;

public record SingletonType(QualifiedTypeName typeName) implements VariantType {
  @Override
  public String typeNameStr() {
    return typeName.name().toString();
  }

  @Override
  public ClassDesc classDesc() {
    return TypeUtils.classDesc(StructBase.class);
  }

  @Override
  public String internalName() {
    return typeName.toString();
  }

  @Override
  public FunctionType constructorType(ParameterizedType returnType) {
    return new FunctionType(List.of(), returnType.typeParameters(), returnType);
  }

  public ClassDesc internalClassDesc() {
    return ClassDesc.ofInternalName(internalName());
  }
}
