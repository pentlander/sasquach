package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

import com.pentlander.sasquach.ast.Identifier;
import java.util.List;

public record LocalFunctionCall(Identifier functionId, List<Expression> arguments,
                                Range range) implements FunctionCall {}
