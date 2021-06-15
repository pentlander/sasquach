package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import org.antlr.v4.runtime.misc.Nullable;

import java.util.List;

public record Function(Scope scope, FunctionSignature functionSignature, Block block, Range range) implements Expression {

    @Override
    public Type type() {
        return returnType();
    }


    public String name() {
        return functionSignature.name();
    }

    public Type returnType() {
        return functionSignature.returnType();
    }

    public List<FunctionParameter> arguments() {
        return functionSignature.parameters();
    }

    public List<Expression> expressions() {
        return block.expressions();
    }

    @Nullable
    public Expression returnExpression() {
        return block.returnExpression();
    }
}
