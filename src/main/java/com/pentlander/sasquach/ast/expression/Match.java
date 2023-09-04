package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Branch;
import java.util.List;

public record Match(Expression expr, List<Branch> branches, Range range) implements Expression {
}
