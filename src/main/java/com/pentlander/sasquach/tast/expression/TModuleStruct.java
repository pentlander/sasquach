package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.ast.TypeStatement;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.Type;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

@RecordBuilder
public record TModuleStruct(QualifiedModuleName name, List<Use> useList, List<TypeStatement> typeStatements,
                            List<TField> fields, List<TNamedFunction> functions, Range range) implements TStructWithName {
  public TModuleStruct {
    requireNonNull(name);
    useList = requireNonNullElse(useList, List.of());
    typeStatements = requireNonNullElse(typeStatements, List.of());
    requireNonNull(fields, "fields");
    requireNonNull(functions, "functions");
    requireNonNull(range, "range");
  }

  @Override
  public StructType structType() {
    var fieldTypes = new LinkedHashMap<UnqualifiedName, Type>();
    functions().forEach(func -> fieldTypes.put(func.name(), func.type()));
    fields().forEach(field -> fieldTypes.put(field.name(), field.type()));

    var namedStructTypes = typeStatements.stream()
        .flatMap(alias -> alias.type() instanceof SumType sumType ? Stream.of(sumType)
            : Stream.empty())
        .collect(toMap(sumType -> sumType.qualifiedTypeName().name(), identity()));
    return new StructType(name(), fieldTypes, namedStructTypes);
  }
}
