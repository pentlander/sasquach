package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record FunctionParameter(String name, Type type, Range.Single identifierRange, Range.Single typeRange) implements Expression {
    public Identifier toIdentifier() {
        return new Identifier(name, type);
    }
}
