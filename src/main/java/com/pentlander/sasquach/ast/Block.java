package com.pentlander.sasquach.ast;

import org.antlr.v4.runtime.misc.Nullable;

import java.util.List;

public record Block(List<Expression> expressions, @Nullable Expression returnExpression) {
}
