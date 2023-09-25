package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TRecur(List<? extends TypedExpression> arguments, Type type, Range range) implements
    TypedExpression {}
