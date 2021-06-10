package com.pentlander.sasquach.ast;

import org.antlr.v4.runtime.misc.Nullable;

public record IfExpression(Expression condition, Block trueBlock, @Nullable Block falseBlock) implements Expression  {
    @Override
    public Type type() {
        return trueBlock.returnExpression().type();
    }
}
