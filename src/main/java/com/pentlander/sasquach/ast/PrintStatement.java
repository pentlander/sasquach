package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;

public record PrintStatement(Expression expression, Range range) implements Expression {

    @Override
    public Type type() {
        return BuiltinType.VOID;
    }
}
