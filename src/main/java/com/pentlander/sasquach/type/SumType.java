package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.runtime.StructBase;
import java.lang.constant.ClassDesc;
import java.util.List;

public record SumType(QualifiedModuleName moduleName, String name,
                      List<TypeParameter> typeParameters, List<VariantType> types) implements Type,
    ParameterizedType {
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
    return moduleName.qualifyInner(typeName()).replace(".", "/");
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    return this.equals(other) || types.stream().anyMatch(type -> type.isAssignableFrom(other));
  }
}
