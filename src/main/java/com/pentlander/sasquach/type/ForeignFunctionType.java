package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import org.jspecify.annotations.Nullable;

public record ForeignFunctionType(DirectMethodHandleDesc methodHandleDesc, @Nullable Type castType) implements
    Type {

  public boolean isConstructor() {
    return methodHandleDesc.kind().equals(Kind.CONSTRUCTOR);
  }

  @Override
  public String typeNameStr() {
    return methodHandleDesc.invocationType().toString();
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
