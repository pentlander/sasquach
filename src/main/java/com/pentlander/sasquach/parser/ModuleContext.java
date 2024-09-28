package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import com.pentlander.sasquach.nameres.NameNotFoundError;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ModuleContext {
  private final SourcePath sourcePath;
  private final SequencedMap<UnqualifiedTypeName, QualifiedTypeName> localTypeNames = new LinkedHashMap<>();
  private final SequencedMap<UnqualifiedName, QualifiedModuleName> moduleNames = new LinkedHashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  public ModuleContext(SourcePath sourcePath) {
    this.sourcePath = sourcePath;
  }

  public SourcePath sourcePath() {
    return sourcePath;
  }

  public void putTypeName(UnqualifiedTypeName typeName, QualifiedTypeName qualTypeName) {
    localTypeNames.put(typeName, qualTypeName);
  }

  @Nullable
  public QualifiedTypeName getTypeName(UnqualifiedTypeName typeName) {
    return localTypeNames.get(typeName);
  }

  public void putModuleName(UnqualifiedName moduleName, QualifiedModuleName qualModuleName) {
    moduleNames.put(moduleName, qualModuleName);
  }

  public QualifiedModuleName getModuleName(Id moduleId) {
    var qualModuleName = moduleNames.get(moduleId.name());
    if (qualModuleName == null) {
      errors.add(new NameNotFoundError(moduleId, "module"));
      return QualifiedModuleName.EMPTY;
    }
    return qualModuleName;
  }

  public RangedErrorList errors() {
    return errors.build();
  }
}
