package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * Package-qualified id used for imports. Packages are separated by '/'.
 *
 */
public record QualifiedModuleId(QualifiedModuleName moduleName, Range.Single range) implements Node,
    Id {
  public QualifiedModuleId(String packageName, String moduleName, Range.Single range) {
    this(new QualifiedModuleName(packageName, moduleName), range);
  }

  public static QualifiedModuleId fromString(String name, Range.Single range) {
    return new QualifiedModuleId(QualifiedModuleName.fromString(name), range);
  }

  public String name() {
    return moduleName.toString();
  }

  public String javaName() {
    return name().replace('/', '.');
  }

  public QualifiedModuleName toQualifiedName() {
    return moduleName;
  }
}
