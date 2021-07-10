package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.type.Type;

public record Value(Type type, String value, Range range) implements Expression {}
