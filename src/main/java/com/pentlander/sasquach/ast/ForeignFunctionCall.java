package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

import java.util.List;

public record ForeignFunctionCall(Identifier classAlias, Identifier functionName,
                                  List<Expression> arguments, Range range) implements Expression {
    public String name() {
        return functionName.name();
    }
}
