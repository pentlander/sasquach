package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;
import org.jspecify.annotations.Nullable;

public record TIfExpression(TypedExpression condition, TypedExpression trueExpression,
                            @Nullable TypedExpression falseExpression, Type type, Range range) implements
    TypedExpression {}
