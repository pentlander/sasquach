package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record ModuleDeclaration(Identifier id, Struct struct, Range range) implements Node {
  public String name() {
    return id.name();
  }
}
