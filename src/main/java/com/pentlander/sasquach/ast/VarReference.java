package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

public record VarReference(Identifier id, Type type) implements Expression {
    public static VarReference of(String name, Type type, Range.Single range) {
        return new VarReference(new Identifier(name, range), type);
    }

    public String name() {
        return id.name();
    }

    @Override
    public Range range() {
        return id.range();
    }
}
