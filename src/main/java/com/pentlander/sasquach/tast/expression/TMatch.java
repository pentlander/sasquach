package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.tast.TBranch;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TMatch(TypedExpression expr, List<TBranch> branches, Type type, Range range) implements TypedExpression {
}
