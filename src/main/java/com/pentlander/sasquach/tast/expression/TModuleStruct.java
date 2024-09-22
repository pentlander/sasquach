package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.List;

@RecordBuilder
public record TModuleStruct(QualifiedModuleName name, List<TypeDef> typeDefs,
                            List<TField> fields, List<TNamedFunction> functions, Range range) implements TStructWithName {
  public TModuleStruct {
    requireNonNull(name);
    requireNonNull(typeDefs);
    requireNonNull(fields, "fields");
    requireNonNull(functions, "functions");
    requireNonNull(range, "range");
  }

  @Override
  public StructType structType() {
    var fieldTypes = new LinkedHashMap<UnqualifiedName, Type>();
    functions().forEach(func -> fieldTypes.put(func.name(), func.type()));
    fields().forEach(field -> fieldTypes.put(field.name(), field.type()));

    return new StructType(name(), fieldTypes);
  }

  public record TypeDef(Type type, SourcePath sourcePath) {}
}
