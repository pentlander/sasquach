package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TBasicFunctionCall(TCallTarget callTarget, UnqualifiedName name,
                                 FunctionType functionType, List<TypedExpression> arguments,
                                 Type returnType, Range range) implements TFunctionCall {
  public sealed interface TCallTarget {
    static Struct struct(TypedExpression structExpr) {
      return new Struct(structExpr);
    }

    static LocalVar localVar(TLocalVariable localVar) {
      return new LocalVar(localVar);
    }

    record Struct(TypedExpression structExpr) implements TCallTarget {}
    record LocalVar(TLocalVariable localVar) implements TCallTarget {}
  }
}
