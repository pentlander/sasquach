package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * Package-qualified id used for imports. Packages are separated by '/'.
 *
 * @param name full string id separated by '/'
 */
public record QualifiedIdentifier(String name, Range.Single range) implements Node, Id {
  public QualifiedIdentifier {
    if (name.contains(".")) {
      throw new IllegalArgumentException(
          "Qualified names must be separated by '/' not '.': " + name);
    }
  }

  public String javaName() {
    return name.replace('/', '.');
  }

  public QualifiedModuleName toQualifiedName() {
    return QualifiedModuleName.fromString(name);
  }
}
