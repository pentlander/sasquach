package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * An import declaration.
 */
public interface Use extends Node {

  /**
   * Fully qualified identifier of the module or class. Qualified imports use '/' as a separator .
   */
  QualifiedIdentifier id();

  /**
   * Fully qualified name of the import.
   */
  default String qualifiedName() {
    return id().name();
  }

  /**
   * Alias for the qualified module or class.
   */
  Identifier alias();

  /**
   * Import for a module.
   */
  record Module(QualifiedIdentifier id, Identifier alias, Range range) implements Use {}

  /**
   * Import for a foreign class.
   */
  record Foreign(QualifiedIdentifier id, Identifier alias, Range range) implements Use {}
}
