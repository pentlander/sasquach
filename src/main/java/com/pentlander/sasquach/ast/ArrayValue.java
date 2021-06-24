package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.ArrayType;
import com.pentlander.sasquach.type.Type;

import java.util.List;

public record ArrayValue(ArrayType type, List<Expression> expressions, Range range) implements Expression {
    public static ArrayValue ofElementType(Type elementType, List<Expression> expressions, Range range) {
        return new ArrayValue(new ArrayType(elementType), expressions, range);
    }

    public Type elementType() {
        return type.elementType();
    }
}
