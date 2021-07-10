package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.InvocationKind;
import java.lang.invoke.MethodType;

public record ForeignFunctionType(MethodType methodType, Type ownerType,
                                  InvocationKind callType) implements Type {
  @Override
  public String typeName() {
    return methodType.toString();
  }

  @Override
  public Class<?> typeClass() {
    throw new IllegalStateException();
  }

  @Override
  public String descriptor() {
    return methodType.descriptorString();
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }
}
