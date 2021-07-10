package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

public record PrintStatement(Expression expression, Range range) implements Expression {}
