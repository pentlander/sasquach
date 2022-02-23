package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.type.Type;

public sealed interface TypeNode<T extends Type> extends Node permits BasicTypeNode,
    FunctionSignature, StructTypeNode {
  T type();

  default String typeName() {
    return type().typeName();
  }

  @Override
  default String toPrettyString() {
    return type().toPrettyString();
  }
}
