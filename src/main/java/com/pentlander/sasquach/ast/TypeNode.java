package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

/**
 * Type that is represented in the source code.
 */
public record TypeNode(Type type, Range range) implements Node {
  @Override
  public String toPrettyString() {
    return type().toPrettyString();
  }
}
