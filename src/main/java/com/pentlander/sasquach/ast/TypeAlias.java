package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record TypeAlias(Identifier id, TypeNode typeNode, Range range) implements Node {
  @Override
  public String toPrettyString() {
    return "type %s = %s".formatted(id.name(), typeNode.toPrettyString());
  }
}