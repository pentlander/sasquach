package com.pentlander.sasquach.type;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Literal types built into the JVM.
 */
public enum BuiltinType implements Type {
  BOOLEAN("Boolean", boolean.class, "Z"),
  INT("Int", int.class, "I"),
  CHAR("Char", char.class, "C"),
  BYTE("Byte", byte.class, "B"),
  SHORT( "Short", short.class, "S"),
  LONG("Long", long.class, "J"),
  FLOAT("Float", float.class, "F"),
  DOUBLE( "Double", double.class, "D"),
  STRING("String", String.class, "Ljava/lang/String;"),
  STRING_ARR( "String[]", String[].class, "[Ljava/lang/String;"),
  VOID("Void", void.class, "V");

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
