package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.type.TypeUtils.typeWithParamsToString;

import com.pentlander.sasquach.ast.QualifiedTypeName;
import com.pentlander.sasquach.runtime.StructBase;
import java.lang.constant.ClassDesc;
import java.util.List;

public record SumType(QualifiedTypeName qualifiedTypeName,
                      List<TypeParameter> typeParameters, List<VariantType> types) implements ParameterizedType,
    TypeNester {
  @Override
  public String typeNameStr() {
    return qualifiedTypeName.simpleName().toString();
  }

  @Override
  public ClassDesc classDesc() {
    return TypeUtils.classDesc(StructBase.class);
  }

  @Override
  public String internalName() {
    return qualifiedTypeName.toString().replace(".", "/");
  }

  public ClassDesc internalClassDesc() {
    return ClassDesc.ofInternalName(internalName());
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

  @Override
  public String toPrettyString() {
    return typeWithParamsToString(qualifiedTypeName().toPrettyString(), typeParameters);
  }
}
