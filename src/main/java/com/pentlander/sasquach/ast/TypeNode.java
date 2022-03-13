package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;

public sealed interface TypeNode<T extends Type> extends Node permits BasicTypeNode,
    FunctionSignature, StructTypeNode, TupleTypeNode, TypeAlias, TypeParameter {
  T type();

  default String typeName() {
    return type().typeName();
  }

  @Override
  default String toPrettyString() {
    return type().toPrettyString();
  }
}
