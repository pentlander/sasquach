package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.TypeId;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.ForeignFunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TForeignFunctionCall(TypeId classAlias, UnqualifiedName name,
                                   ForeignFunctionType foreignFunctionType,
                                   List<TypedExpression> arguments, Type returnType,
                                   Range range) implements TFunctionCall {
}
