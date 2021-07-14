package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Struct;
import io.soabase.recordbuilder.core.RecordBuilder;

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
}
