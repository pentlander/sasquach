package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.NamedType;
import java.util.List;

public record NamedTypeNode(TypeIdentifier id, List<TypeNode> typeArgumentNodes, NamedType type, Range range) implements TypeNode {
  public NamedTypeNode(TypeIdentifier id, List<TypeNode> typeArgumentNodes, Range range) {
    this(
        id,
        typeArgumentNodes,
        new NamedType(id, typeArgumentNodes.stream().map(TypeNode::type).toList()),
        range);
  }
}
