package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.StructName;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeUtils;
import java.util.LinkedHashMap;
import java.util.List;

public record TLiteralStruct(StructName name, List<TField> fields,
                             List<TVarReference> spreads,
                             Range range) implements TStruct {
  public TLiteralStruct {
    requireNonNull(fields, "fields");
    requireNonNull(spreads);
    requireNonNull(range, "range");
  }

  @Override
  public StructType structType() {
    var fieldTypes = new LinkedHashMap<UnqualifiedName, Type>();
    fields.forEach(field -> fieldTypes.put(field.name(), field.type()));
    spreads.forEach(varRef -> {
      var structType = TypeUtils.asStructType(varRef.type()).orElseThrow();
      fieldTypes.putAll(structType.memberTypes());
    });
    return new StructType(name, fieldTypes);
  }
}
