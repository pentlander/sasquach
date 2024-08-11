package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.ast.QualifiedTypeName;
import com.pentlander.sasquach.runtime.StructBase;
import java.lang.constant.ClassDesc;
import java.util.List;

public record SumType(QualifiedTypeName qualifiedTypeName,
                      List<TypeParameter> typeParameters, List<VariantType> types) implements Type,
    ParameterizedType {
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
    return qualifiedTypeName.toString().replace(".", "/");
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
