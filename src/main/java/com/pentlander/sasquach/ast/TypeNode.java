package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.type.Type;

public sealed interface TypeNode extends Node permits BasicTypeNode, ConstructableNamedTypeNode,
    FunctionSignature, NamedTypeNode, StructTypeNode, SumTypeNode, VariantTypeNode, TupleTypeNode,
    TypeStatement {
  Type type();

  default String typeNameStr() {
    return type().typeNameStr();
  }

  @Override
  default String toPrettyString() {
    return type().toPrettyString();
  }
}
