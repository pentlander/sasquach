package com.pentlander.sasquach.ast.typenode;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.ArrayType;
import com.pentlander.sasquach.type.Type;

public record ArrayTypeNode(TypeNode typeArgumentNode, Range range) implements TypeNode {
  @Override
  public Type type() {
    return new ArrayType(typeArgumentNode.type());
  }
}
