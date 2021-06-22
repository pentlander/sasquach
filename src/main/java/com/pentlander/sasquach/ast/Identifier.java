package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record Identifier(String name, Type type, Range.Single range) implements Expression {
}
