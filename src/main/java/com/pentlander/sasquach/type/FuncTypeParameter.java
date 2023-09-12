package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;

public record FuncTypeParameter(String typeName) implements Type {

  @Override
  public ClassDesc classDesc() {
    throw new IllegalStateException();
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }
}
