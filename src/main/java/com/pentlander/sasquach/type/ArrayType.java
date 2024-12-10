package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;

/**
 * Type of an array.
 */
public record ArrayType(Type elementType) implements Type, TypeNester {
  public static final String TYPE_NAME = "Array";

  @Override
  public String typeNameStr() {
    return elementType.typeNameStr() + "[]";
  }

  @Override
  public ClassDesc classDesc() {
    return elementType.classDesc().arrayType();
  }

  @Override
  public String internalName() {
    return classDesc().descriptorString();
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    return other instanceof ArrayType otherType && elementType.isAssignableFrom(otherType.elementType());
  }
}
