package com.pentlander.sasquach.type;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Literal types built into the JVM.
 */
public enum BuiltinType implements Type {
  BOOLEAN("boolean", boolean.class, "Z"), INT("int", int.class, "I"), CHAR(
      "char",
      char.class,
      "C"), BYTE("byte", byte.class, "B"), SHORT("short", short.class, "S"), LONG(
      "long",
      long.class,
      "J"), FLOAT("float", float.class, "F"), DOUBLE("double", double.class, "D"), STRING(
      "string",
      String.class,
      "Ljava/lang/String;"), STRING_ARR("string[]", String[].class, "[Ljava/lang/String;"), VOID(
      "void",
      void.class,
      "V");

  private final String name;
  private final Class<?> typeClass;
  private final String descriptor;

  BuiltinType(String name, Class<?> typeClass, String descriptor) {
    this.name = name;
    this.typeClass = typeClass;
    this.descriptor = descriptor;
  }

  public static BuiltinType fromString(String value) {
    return Arrays.stream(BuiltinType.values()).filter(type -> type.name.equals(value)).findFirst()
        .orElseThrow(() -> new NoSuchElementException(value));
  }

  @Override
  public String typeName() {
    return name;
  }

  @Override
  public Class<?> typeClass() {
    return typeClass;
  }

  @Override
  public String descriptor() {
    return descriptor;
  }

  @Override
  public String internalName() {
    return org.objectweb.asm.Type.getInternalName(typeClass);
  }
}
