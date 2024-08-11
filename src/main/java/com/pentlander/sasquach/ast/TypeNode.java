package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;

public sealed interface TypeNode extends Node permits BasicTypeNode, FunctionSignature,
    StructTypeNode, SumTypeNode, VariantTypeNode, TupleTypeNode, TypeAlias {
  Type type();

  default String typeNameStr() {
    return type().typeNameStr();
  }

  @Override
  default String toPrettyString() {
    return type().toPrettyString();
  }
}
