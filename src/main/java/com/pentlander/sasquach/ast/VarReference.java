package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

public record VarReference(Identifier id) implements Expression {
    public static VarReference of(String name, Range.Single range) {
        return new VarReference(new Identifier(name, range));
    }

    public String name() {
        return id.name();
    }

    @Override
    public Range range() {
        return id.range();
    }
}
