package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FunctionCall;
import java.util.List;

public record ForeignFunctionCall(Identifier classAlias, Identifier functionId,
                                  List<Expression> arguments, Range range) implements
    FunctionCall {}
