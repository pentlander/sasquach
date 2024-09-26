package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ModuleContext {
  private final SourcePath sourcePath;
  private final SequencedMap<UnqualifiedTypeName, QualifiedTypeName> localTypeNames = new LinkedHashMap<>();
  private final SequencedMap<UnqualifiedName, QualifiedModuleName> moduleNames = new LinkedHashMap<>();

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

  public QualifiedModuleName getModuleName(UnqualifiedName moduleName) {
    return Objects.requireNonNull(
        moduleNames.get(moduleName),
        "Could not resolve module name: " + moduleName);
  }
}
