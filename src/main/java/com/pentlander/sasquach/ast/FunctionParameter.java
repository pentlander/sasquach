package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record FunctionParameter(String name, Type type, Range.Single identifierRange, Range.Single typeRange) implements Expression {
    @Override
    public Range range() {
        return identifierRange.join(typeRange);
    }

    public Identifier toIdentifier() {
        return new Identifier(name, type, identifierRange);
    }
}
