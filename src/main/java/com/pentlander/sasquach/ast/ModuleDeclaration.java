package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.ast.expression.Struct;

/**
 * Declaration of a module struct.
 */
public record ModuleDeclaration(QualifiedModuleId id, ModuleStruct struct, Range range) implements Node {
  /**
   * Module captureName.
   */
  public QualifiedModuleName name() {
    return id.name();
  }

  @Override
  public String toPrettyString() {
    return name() + " " + struct.toPrettyString();
  }
}
