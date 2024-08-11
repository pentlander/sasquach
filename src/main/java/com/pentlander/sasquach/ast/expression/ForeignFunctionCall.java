package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.TypeId;
import java.util.List;

public record ForeignFunctionCall(TypeId classAlias, Id functionId,
                                  List<Expression> arguments, Range range) implements
    FunctionCall {}
