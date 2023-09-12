package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;

public record ForeignFunctionType(DirectMethodHandleDesc methodHandleDesc, Type castType) implements
    Type {


  @Override
  public String typeName() {
    return methodHandleDesc.invocationType().toString();
  }

  public Kind methodKind() {
    return methodHandleDesc.kind();
  }

  public ClassDesc ownerDesc() {
    return methodHandleDesc.owner();
  }

  @Override
  public ClassDesc classDesc() {
    throw new IllegalStateException();
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }
}
