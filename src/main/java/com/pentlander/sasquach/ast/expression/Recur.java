package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import java.util.List;

public record Recur(List<Expression> arguments, Range range) implements Expression {}
