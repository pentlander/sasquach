package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.ast.QualifiedTypeName;
import com.pentlander.sasquach.runtime.StructBase;
import java.lang.constant.ClassDesc;

public record SingletonType(QualifiedTypeName qualifiedTypeName) implements VariantType {
  @Override
  public String typeNameStr() {
    return qualifiedTypeName.name().toString();
  }

  @Override
  public ClassDesc classDesc() {
    return TypeUtils.classDesc(StructBase.class);
  }

  @Override
  public String internalName() {
    return qualifiedTypeName.toString();
  }

}
