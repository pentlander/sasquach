package com.pentlander.sasquach.type;

import java.util.Map;
import java.util.Map.Entry;

public record StructType(String typeName, Map<String, Type> fieldTypes) implements Type {
  public StructType(Map<String, Type> fieldTypes) {
    this("AnonStruct$" + hashFieldTypes(fieldTypes), fieldTypes);
  }

  private static String hashFieldTypes(Map<String, Type> fieldTypes) {
    return Integer.toHexString(fieldTypes.entrySet().stream().sorted(Entry.comparingByKey()).toList().hashCode());
  }

  @Override
  public Class<?> typeClass() {
    return null;
  }

  @Override
  public String descriptor() {
    return "L%s;".formatted(internalName());
  }

  @Override
  public String internalName() {
    return typeName().replace(".", "/");
  }
}
