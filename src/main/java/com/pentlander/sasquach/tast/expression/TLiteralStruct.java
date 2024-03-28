package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.List;

@RecordBuilder
public record TLiteralStruct(List<TField> fields, List<TNamedFunction> functions,
                             Range range) implements TStruct {
  public TLiteralStruct {
    requireNonNull(fields, "fields");
    requireNonNull(functions, "functions");
    requireNonNull(range, "range");
  }

  @Override
  public StructType structType() {
    var fieldTypes = new LinkedHashMap<String, Type>();
    functions.forEach(func -> fieldTypes.put(func.name(), func.type()));
    fields.forEach(field -> fieldTypes.put(field.name(), field.type()));
    return new StructType(fieldTypes);
  }
}
