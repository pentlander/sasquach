package com.pentlander.sasquach.type;

import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.runtime.StructBase;
import java.lang.constant.ClassDesc;
import java.util.List;

public record SingletonType(QualifiedTypeName name) implements VariantType {
  @Override
  public String typeNameStr() {
    return name.simpleName().toString();
  }

  @Override
  public ClassDesc classDesc() {
    return StructBase.CD;
  }

  @Override
  public String internalName() {
    return name.toString();
  }

  @Override
  public FunctionType constructorType(ParameterizedType returnType) {
    return new FunctionType(List.of(), returnType.typeParameters(), returnType);
  }

  public ClassDesc internalClassDesc() {
    return ClassDesc.ofInternalName(internalName());
  }
}
