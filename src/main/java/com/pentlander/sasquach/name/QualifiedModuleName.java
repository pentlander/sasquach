package com.pentlander.sasquach.name;

import com.pentlander.sasquach.PackageName;
import java.lang.constant.ClassDesc;
import java.util.List;

public record QualifiedModuleName(PackageName packageName, String moduleName) implements QualifiedName {
  public static final QualifiedModuleName EMPTY = new QualifiedModuleName(new PackageName("EMPTY"), "EMPTY");

  public static QualifiedModuleName fromString(String qualifiedModuleName) {
    var lastSlash = qualifiedModuleName.lastIndexOf("/");
    if (lastSlash == -1 || lastSlash == qualifiedModuleName.length() - 1) {
      throw new IllegalStateException("Invalid qualified module name: " + qualifiedModuleName);
    }
    return new QualifiedModuleName(
        new PackageName(qualifiedModuleName.substring(0, lastSlash)),
        qualifiedModuleName.substring(lastSlash + 1));
  }

  public UnqualifiedName simpleName() {
    return new UnqualifiedName(moduleName);
  }

  public QualifiedTypeName toQualifiedTypeName() {
    return new QualifiedTypeName(qualifiedModuleName(), List.of());
  }

  public QualifiedTypeName qualifyInner(UnqualifiedTypeName name) {
    return new QualifiedTypeName(this, name);
  }

  public String javaName() {
    return toString().replace("/", ".");
  }

  @Override
  public String toString() {
    return packageName + "/" + moduleName;
  }

  @Override
  public QualifiedModuleName qualifiedModuleName() {
    return this;
  }

  public ClassDesc toClassDesc() {
    return ClassDesc.ofInternalName(toString());
  }
}
