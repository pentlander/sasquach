package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record Value(Type type, String value, Range range) implements Expression {
}
