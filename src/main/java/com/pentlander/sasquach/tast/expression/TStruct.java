package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.Util;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.tast.TypedMember;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public sealed interface TStruct extends TypedExpression permits TLiteralStruct, TStructWithName {
  List<TField> fields();

  StructType structType();

  default List<Type> constructorParams() {
    return fields().stream().map(TField::type).toList();
  }

  @Override
  default Type type() {
    return structType();
  }

  record TField(Id id, TypedExpression expr) implements TypedNode, TypedMember {
    public UnqualifiedName name() {
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
