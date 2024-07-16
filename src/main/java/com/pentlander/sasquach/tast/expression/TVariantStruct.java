package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.Type;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record TVariantStruct(String name, List<TField> fields, List<TNamedFunction> functions,
                             List<Type> constructorParams, SumType type, Range range) implements
    TStructWithName {
  public TVariantStruct {
    requireNonNull(name);
    requireNonNull(fields, "fields");
    requireNonNull(functions, "functions");
    requireNonNull(range, "range");
  }
}
