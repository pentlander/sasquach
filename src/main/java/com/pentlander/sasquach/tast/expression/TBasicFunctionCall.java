package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TBasicFunctionCall(TCallTarget callTarget, UnqualifiedName name,
                                 FunctionType functionType, TArgs typedArgs,
                                 Type returnType, Range range) implements TFunctionCall {
  @Override
  public List<TypedExpression> arguments() {
    return typedArgs.args();
  }

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

  public record TArgs(int[] argIndexes, List<TypedExpression> args) {}
}
