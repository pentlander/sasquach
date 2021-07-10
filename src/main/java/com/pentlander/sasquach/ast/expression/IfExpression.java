package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import org.antlr.v4.runtime.misc.Nullable;

public record IfExpression(Expression condition, Expression trueExpression,
                           @Nullable Expression falseExpression, Range range) implements
    Expression {}
