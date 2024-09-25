package com.pentlander.sasquach.ast.typenode;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.BuiltinType;

/**
 * Type that is represented in the source code.
 */
public record BasicTypeNode(BuiltinType type, Range range) implements TypeNode {
  @Override
  public String toPrettyString() {
    return type().toPrettyString();
  }
}
