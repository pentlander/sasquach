package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import org.jspecify.annotations.Nullable;

public record IfExpression(Expression condition, Expression trueExpression,
                           @Nullable Expression falseExpression, Range range) implements
    Expression {}
