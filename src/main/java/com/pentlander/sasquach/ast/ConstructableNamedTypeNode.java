package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;

public sealed interface ConstructableNamedTypeNode extends TypeNode permits VariantTypeNode, StructTypeNode, TupleTypeNode {}
