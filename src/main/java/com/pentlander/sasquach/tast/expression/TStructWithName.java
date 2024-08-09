package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.ast.StructName;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import java.util.LinkedHashMap;

public sealed interface TStructWithName extends TStruct permits TModuleStruct, TNamedStruct,
    TVariantStruct {
  StructName name();

  default StructType structType() {
    var fieldTypes = new LinkedHashMap<String, Type>();
    fields().forEach(field -> fieldTypes.put(field.name(), field.type()));
    return new StructType(name(), fieldTypes);
  }
}
