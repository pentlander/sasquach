package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

public record TIfExpression(TypedExpression condition, TypedExpression trueExpression,
                            TypedExpression falseExpression, Type type, Range range) implements
    TypedExpression {}
