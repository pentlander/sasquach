package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

public record IfExpression(Expression condition, Expression trueExpression,
                           Expression falseExpression, Range range) implements
    Expression {}
