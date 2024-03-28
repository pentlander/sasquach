package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.Struct.StructKind;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.tast.TypedMember;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import java.util.List;
import java.util.Optional;

public sealed interface TStruct extends TypedExpression permits TLiteralStruct, TStructWithName {
  List<TField> fields();

  List<TNamedFunction> functions();

  StructType structType();

  @Override
  default Type type() {
    return structType();
  }

  record TField(Identifier id, TypedExpression expr) implements TypedNode, TypedMember {
    public String name() {
      return id.name();
    }

    @Override
    public Type type() {
      return expr.type();
    }

    @Override
    public Range range() {
      return id.range().join(expr.range());
    }
  }
}
