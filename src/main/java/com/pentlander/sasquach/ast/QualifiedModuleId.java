package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.Range;
import java.lang.constant.ClassDesc;

/**
 * Package-qualified id used for imports. Packages are separated by '/'.
 *
 */
public record QualifiedModuleId(QualifiedModuleName moduleName, Range.Single range) implements Node,
    Identifier {
  public QualifiedModuleId(PackageName packageName, String moduleName, Range.Single range) {
    this(new QualifiedModuleName(packageName, moduleName), range);
  }

  public static QualifiedModuleId fromString(String name, Range.Single range) {
    return new QualifiedModuleId(QualifiedModuleName.fromString(name), range);
  }

  public QualifiedModuleName name() {
    return moduleName;
  }

  public ClassDesc toClassDesc() {
    return ClassDesc.ofInternalName(name().toString());
  }
}
