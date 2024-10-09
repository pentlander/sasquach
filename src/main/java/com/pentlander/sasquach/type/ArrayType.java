package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;

/**
 * Type of an array.
 */
public record ArrayType(Type elementType) implements Type {
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
}
