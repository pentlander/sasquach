package com.pentlander.sasquach.ast.typenode;

import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.type.Type;

public sealed interface TypeNode extends Node permits ArrayTypeNode, BasicTypeNode,
    ConstructableNamedTypeNode, FunctionSignature, NamedTypeNode, StructTypeNode, SumTypeNode,
    VariantTypeNode, TupleTypeNode, TypeStatement {
  Type type();

  default String typeNameStr() {
    return type().typeNameStr();
  }

  @Override
  default String toPrettyString() {
    return type().toPrettyString();
  }
}
