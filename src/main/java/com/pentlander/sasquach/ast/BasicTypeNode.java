package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

/**
 * Type that is represented in the source code.
 */
public record BasicTypeNode<T extends Type>(T type, Range range) implements TypeNode {
  @Override
  public String toPrettyString() {
    return type().toPrettyString();
  }
}
