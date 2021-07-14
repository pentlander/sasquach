package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * Package-qualified identifier used for imports. Packages are separated by '/'.
 *
 * @param name full string identifier separated by '/'
 */
public record QualifiedIdentifier(String name, Range.Single range) implements Node, Id {
  public QualifiedIdentifier {
    if (name.contains(".")) {
      throw new IllegalArgumentException(
          "Qualified names must be separated by '/' not '.': " + name);
    }
  }

  public String unqualifiedName() {
    var parts =  name.split("/");
    return parts[parts.length - 1];
  }
}
