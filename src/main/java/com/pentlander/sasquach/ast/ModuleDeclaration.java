package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Struct;

/**
 * Declaration of a module struct.
 */
public record ModuleDeclaration(QualifiedIdentifier id, Struct struct, Range range) implements Node {
  /**
   * Module name.
   */
  public String name() {
    return id.name();
  }

  @Override
  public String toPrettyString() {
    return name() + " " + struct.toPrettyString();
  }
}
