package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

public record FunctionParameter(Identifier id, TypeNode typeNode) implements Node {
    public String name() {
        return id.name();
    }

    public Type type() {
        return typeNode.type();
    }

    @Override
    public Range range() {
        return id.range().join(typeNode.range());
    }

    public VarReference toReference() {
        return new VarReference(id);
    }
}
