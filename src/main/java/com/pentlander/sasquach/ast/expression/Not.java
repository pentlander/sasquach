package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

public record Not(Expression expression, Range range) implements Expression {}
