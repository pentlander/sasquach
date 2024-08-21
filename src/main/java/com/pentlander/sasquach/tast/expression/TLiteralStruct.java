package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeUtils;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.List;

@RecordBuilder
@RecordBuilder.Options(addSingleItemCollectionBuilders = true, useImmutableCollections = true)
public record TLiteralStruct(List<TField> fields,
                             List<TVarReference> spreads,
                             Range range) implements TStruct {
  public TLiteralStruct {
    requireNonNull(fields, "fields");
    spreads = requireNonNullElse(spreads, List.of());
    requireNonNull(range, "range");
  }

  @Override
  public StructType structType() {
    var fieldTypes = new LinkedHashMap<UnqualifiedName, Type>();
    fields.forEach(field -> fieldTypes.put(field.name(), field.type()));
    spreads.forEach(varRef -> {
      var structType = TypeUtils.asStructType(varRef.type()).orElseThrow();
      fieldTypes.putAll(structType.fieldTypes());
    });
    return StructType.unnamed(fieldTypes);
  }
}
