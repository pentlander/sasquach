package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public interface Use extends Node {

  /**
   * Fully qualified identifier of the module or class. Qualified imports use '/' as a separator .
   */
  QualifiedIdentifier id();

  default String qualifiedName() {
    return id().name();
  }

  /** Alias for the qualified module or class. */
  Identifier alias();

  record Module(QualifiedIdentifier id, Identifier alias, Range range) implements Use {}

  record Foreign(QualifiedIdentifier id, Identifier alias, Range range) implements Use {}
}
