package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.runtime.StructBase;
import java.lang.constant.ClassDesc;

public record SingletonType(QualifiedModuleName moduleName, String name) implements VariantType {
  @Override
  public String typeName() {
    return name;
  }

  @Override
  public ClassDesc classDesc() {
    return TypeUtils.classDesc(StructBase.class);
  }

  @Override
  public String internalName() {
    return moduleName.qualifyInner(name);
  }
}
