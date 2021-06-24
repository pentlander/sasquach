package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

public record FunctionParameter(Identifier id, Type type, Range.Single typeRange) implements Expression {
    public String name() {
        return id.name();
    }

    @Override
    public Range range() {
        return id.range().join(typeRange);
    }

    public VarReference toReference() {
        return new VarReference(id, type);
    }
}
