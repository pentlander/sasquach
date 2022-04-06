package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleName;
import java.util.List;

public record SumType(QualifiedModuleName moduleName, String name, List<VariantType> types) implements Type {
  @Override
  public String typeName() {
    return null;
  }

  @Override
  public Class<?> typeClass() {
    return null;
  }

  @Override
  public String descriptor() {
    return null;
  }

  @Override
  public String internalName() {
    return null;
  }
}
