package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.RecurPoint;
import java.util.List;

public record Loop(List<VariableDeclaration> varDeclarations, Expression expression,
                   Range range) implements Expression, RecurPoint {}
