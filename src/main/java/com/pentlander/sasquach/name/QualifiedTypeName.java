package com.pentlander.sasquach.name;

import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Objects;

public record QualifiedTypeName(QualifiedModuleName qualifiedModuleName,
                                List<UnqualifiedTypeName> names) implements Name, StructName,
    QualifiedName {

  public QualifiedTypeName(QualifiedModuleName qualifiedModuleName, UnqualifiedTypeName name) {
    this(qualifiedModuleName, List.of(name));
  }

  @Override
  public UnqualifiedTypeName simpleName() {
    return names.isEmpty() ? new UnqualifiedTypeName(qualifiedModuleName.moduleName())
        : names.getLast();
  }

  @Override
  public String toString() {
    var suffix =
        !names.isEmpty() ? "$" + names.stream().map(Objects::toString).collect(joining("$")) : "";
    return qualifiedModuleName + suffix;
  }

  @Override
  public String toPrettyString() {
    return toString().replace('$', '.');
  }
}
