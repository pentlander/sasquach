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
    return moduleName.qualifyInner(typeName()).toString().replace(".", "/");
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    if (other instanceof SumType sumType) {
      for (int i = 0; i < types.size(); i++) {
        var type = types.get(i);
        var otherType = sumType.types.get(i);
        if (!type.isAssignableFrom(otherType)) {
          return false;
        }
      }
      return true;
    }
    return types.stream().anyMatch(type -> type.isAssignableFrom(other));
  }
}
