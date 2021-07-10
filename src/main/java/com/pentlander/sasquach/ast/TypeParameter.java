package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record TypeParameter(Identifier id) implements Node {
  @Override
  public Range range() {
    return id().range();
  }
}
