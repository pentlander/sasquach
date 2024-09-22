package com.pentlander.sasquach.ast;

import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.Preconditions;
import java.util.List;
import java.util.Objects;

public record QualifiedTypeName(QualifiedModuleName qualifiedModuleName,
                                List<UnqualifiedTypeName> names) implements Name, StructName,
    QualifiedName {
  public QualifiedTypeName {
    Preconditions.checkArgument(!names.isEmpty(), "names must not be empty");
  }

  public QualifiedTypeName(QualifiedModuleName qualifiedModuleName, UnqualifiedTypeName name) {
    this(qualifiedModuleName, List.of(name));
  }

  @Override
  public UnqualifiedTypeName simpleName() {
    return names.getLast();
  }

  @Override
  public String toString() {
    return qualifiedModuleName + "$" + names.stream()
        .map(Objects::toString)
        .collect(joining("$"));
  }

  @Override
  public String toPrettyString() {
    return qualifiedModuleName + "." + names.stream()
        .map(Objects::toString)
        .collect(joining("."));
  }
}
