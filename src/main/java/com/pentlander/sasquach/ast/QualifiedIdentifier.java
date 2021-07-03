package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * Package-qualified identifier used for imports. Packages are separated by '/'.
 *
 * @param name full string identifier separated by '/'
 */
public record QualifiedIdentifier(String name, Range.Single range) implements Node {}
