package com.pentlander.sasquach.ast;

public record QualifiedModuleName(String packageName, String moduleName) {
  public static QualifiedModuleName fromString(String qualifiedModuleName) {
    var lastSlash = qualifiedModuleName.lastIndexOf("/");
    if (lastSlash == -1 || lastSlash == qualifiedModuleName.length() - 1) {
      throw new IllegalStateException("Invalid qualified module captureName: " + qualifiedModuleName);
    }
    return new QualifiedModuleName(
        qualifiedModuleName.substring(0, lastSlash),
        qualifiedModuleName.substring(lastSlash + 1));
  }

  public String qualifyInner(String name) {
    return this + "$" + name;
  }

  public String javaName() {
    return toString().replace("/", ".");
  }

  @Override
  public String toString() {
    return packageName + "/" + moduleName;
  }
}
