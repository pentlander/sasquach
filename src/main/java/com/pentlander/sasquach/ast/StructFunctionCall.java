package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import java.util.List;

public record StructFunctionCall(Expression structExpression, Identifier functionId,
                                 List<Expression> arguments, Range range) implements FunctionCall {
}
