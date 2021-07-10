package com.pentlander.sasquach.type;

import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.runtime.StructBase;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @param fieldTypes Field types include any value type, as well as functions.
 */
public record StructType(String typeName, Map<String, Type> fieldTypes) implements ParameterizedType {
  public StructType(Map<String, Type> fieldTypes) {
    this("AnonStruct$" + hashFieldTypes(fieldTypes), fieldTypes);
  }

  private static String hashFieldTypes(Map<String, Type> fieldTypes) {
    return Integer.toHexString(fieldTypes.entrySet().stream().sorted(Entry.comparingByKey()).toList().hashCode());
  }

  public Type fieldType(String fieldName) {
    return fieldTypes().get(fieldName);
  }

  @Override
  public Class<?> typeClass() {
    return StructBase.class;
  }

  @Override
  public String descriptor() {
    return "L%s;".formatted(StructBase.class.getName().replace('.', '/'));
//    return "L%s;".formatted(internalName());
  }

  @Override
  public String internalName() {
    return typeName().replace(".", "/");
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    if (other instanceof StructType structType) {
      for (var entry : fieldTypes.entrySet()) {
        var name = entry.getKey();
        var type = entry.getValue();
        var otherType = structType.fieldTypes().get(name);
        if (otherType == null || !type.isAssignableFrom(otherType)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public String toPrettyString() {
    return typeName().startsWith("AnonStruct$") ? fieldTypes().entrySet().stream()
        .map(e -> e.getKey() + ": " + e.getValue().toPrettyString())
        .collect(joining(", ", "{ ", " }")) : typeName();
  }
}
