package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * An import declaration.
 */
public sealed interface Use extends Node {

  /**
   * Fully qualified id of the module or class. Qualified imports use '/' as a separator .
   */
  QualifiedModuleId id();

  /**
   * Fully qualified captureName of the import.
   */
  default String qualifiedName() {
    return id().name();
  }

  /**
   * Alias for the qualified module or class.
   */
  Id alias();

  /**
   * Import for a module.
   */
  record Module(QualifiedModuleId id, Id alias, Range range) implements Use {}

  /**
   * Import for a foreign class.
   */
  record Foreign(QualifiedModuleId id, Id alias, Range range) implements Use {}
}
