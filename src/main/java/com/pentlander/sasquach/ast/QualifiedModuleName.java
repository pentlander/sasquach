package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.PackageName;

public record QualifiedModuleName(PackageName packageName, String moduleName) implements StructName, QualifiedName {
  public static QualifiedModuleName fromString(String qualifiedModuleName) {
    var lastSlash = qualifiedModuleName.lastIndexOf("/");
    if (lastSlash == -1 || lastSlash == qualifiedModuleName.length() - 1) {
      throw new IllegalStateException("Invalid qualified module name: " + qualifiedModuleName);
    }
    return new QualifiedModuleName(
        new PackageName(qualifiedModuleName.substring(0, lastSlash)),
        qualifiedModuleName.substring(lastSlash + 1));
  }

  public QualifiedTypeName qualifyInner(UnqualifiedTypeName name) {
    return new QualifiedTypeName(this, name);
  }

  public QualifiedTypeName qualifyInner(UnqualifiedName name) {
    return new QualifiedTypeName(this, new UnqualifiedTypeName(name.value()));
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
}
