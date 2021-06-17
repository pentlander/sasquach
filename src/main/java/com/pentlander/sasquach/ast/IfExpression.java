package com.pentlander.sasquach.ast;

import org.antlr.v4.runtime.misc.Nullable;

public record IfExpression(Expression condition, Expression trueExpression, @Nullable Expression falseExpression) implements Expression  {
    @Override
    public Type type() {
        return trueExpression.type();
    }
}
