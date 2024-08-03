package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.QualifiedStructName;
import com.pentlander.sasquach.tast.TNamedFunction;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record TNamedStruct(QualifiedStructName name, List<TField> fields, List<TNamedFunction> functions,
                           Range range) implements TStructWithName {
  public TNamedStruct {
    requireNonNull(name);
    requireNonNull(fields, "fields");
    requireNonNull(functions, "functions");
    requireNonNull(range, "range");
  }
}
