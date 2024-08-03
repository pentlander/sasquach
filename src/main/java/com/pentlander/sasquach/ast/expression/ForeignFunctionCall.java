package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

import com.pentlander.sasquach.ast.Id;
import java.util.List;

public record ForeignFunctionCall(Id classAlias, Id functionId,
                                  List<Expression> arguments, Range range) implements
    FunctionCall {}
