package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.tast.TNamedFunction;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record TModuleStruct(QualifiedModuleName name, List<Use> useList, List<TypeAlias> typeAliases,
                            List<TField> fields, List<TNamedFunction> functions, Range range) implements TStructWithName {
  public TModuleStruct {
    name = requireNonNull(name);
    useList = requireNonNullElse(useList, List.of());
    typeAliases = requireNonNullElse(typeAliases, List.of());
    requireNonNull(fields, "fields");
    requireNonNull(functions, "functions");
    requireNonNull(range, "range");
  }
}
