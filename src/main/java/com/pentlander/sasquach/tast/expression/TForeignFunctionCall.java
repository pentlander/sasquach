package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.type.ArrayType;
import com.pentlander.sasquach.type.ForeignFunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TForeignFunctionCall(TypeId classAlias, UnqualifiedName name,
                                   ForeignFunctionType foreignFunctionType,
                                   List<TypedExpression> arguments, Varargs varargs,
                                   Type returnType, Range range) implements TFunctionCall {

  public sealed interface Varargs {
    static None none() {
      return None.INSTANCE;
    }

    static Some some(ArrayType type, int varargsIdx) {
      return new Some(type, varargsIdx);
    }

    final class None implements Varargs {
      private static final None INSTANCE = new None();
      private None() {}
    }

    record Some(ArrayType type, int varargsIdx) implements Varargs {}
  }
}
