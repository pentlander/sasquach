package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * An unqualified identifier.
 */
public record Identifier(String name, Range.Single range) implements Node {}
