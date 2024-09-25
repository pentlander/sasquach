package com.pentlander.sasquach.ast.typenode;

import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode;

public sealed interface ConstructableNamedTypeNode extends TypeNode permits VariantTypeNode, StructTypeNode, TupleTypeNode {}
