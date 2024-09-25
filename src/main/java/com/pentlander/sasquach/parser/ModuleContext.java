package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ModuleContext {
  private final SourcePath sourcePath;
  private final SequencedMap<UnqualifiedTypeName, QualifiedTypeName> localTypeNames = new LinkedHashMap<>();

  public ModuleContext(SourcePath sourcePath) {
    this.sourcePath = sourcePath;
  }

  public SourcePath sourcePath() {
    return sourcePath;
  }

  public void putTypeName(UnqualifiedTypeName typeName, QualifiedTypeName qualTypeName) {
    localTypeNames.put(typeName, qualTypeName);
  }

  public QualifiedTypeName getTypeName(UnqualifiedTypeName typeName) {
    return Objects.requireNonNull(
        localTypeNames.get(typeName),
        "Could not resolve type name: " + typeName);
  }
}
