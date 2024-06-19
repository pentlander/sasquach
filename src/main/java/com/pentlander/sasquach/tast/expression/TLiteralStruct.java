package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeUtils;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.List;

@RecordBuilder
public record TLiteralStruct(List<TField> fields, List<TNamedFunction> functions,
                             List<TVarReference> spreads,
                             Range range) implements TStruct {
  public TLiteralStruct {
    requireNonNull(fields, "fields");
    requireNonNull(functions, "functions");
    spreads = requireNonNullElse(spreads, List.of());
    requireNonNull(range, "range");
  }

  @Override
  public StructType structType() {
    var fieldTypes = new LinkedHashMap<String, Type>();
    functions.forEach(func -> fieldTypes.put(func.name(), func.type()));
    fields.forEach(field -> fieldTypes.put(field.name(), field.type()));
    spreads.forEach(varRef -> {
      var structType = TypeUtils.asStructType(varRef.type()).orElseThrow();
      fieldTypes.putAll(structType.fieldTypes());
    });
    return new StructType(fieldTypes);
  }
}
