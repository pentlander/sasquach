package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Expression;

public record Branch(Pattern pattern, Expression expr, Range range) implements Node {
}
