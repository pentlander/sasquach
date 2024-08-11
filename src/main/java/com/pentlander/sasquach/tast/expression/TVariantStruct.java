package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.QualifiedTypeName;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.Type;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record TVariantStruct(QualifiedTypeName name, List<TField> fields,
                             List<Type> constructorParams, SumType type, Range range) implements
    TStructWithName {
  public TVariantStruct {
    requireNonNull(name);
    requireNonNull(fields, "fields");
    requireNonNull(range, "range");
  }
}
