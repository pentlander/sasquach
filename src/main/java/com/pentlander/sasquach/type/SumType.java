package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.runtime.StructBase;
import java.util.List;

public record SumType(QualifiedModuleName moduleName, String name, List<VariantType> types) implements Type,
    ParameterizedType {
  @Override
  public String typeName() {
    return name;
  }

  @Override
  public Class<?> typeClass() {
    return StructBase.class;
  }

  @Override
  public String descriptor() {
    return StructBase.class.descriptorString();
  }

  @Override
  public String internalName() {
    return (moduleName.toString()  + "$" + typeName()).replace(".", "/");
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    return this.equals(other) || types.stream().anyMatch(type -> type.isAssignableFrom(other));
  }
}
