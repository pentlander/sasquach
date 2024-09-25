package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.QualifiedTypeName;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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

  @Nullable
  public QualifiedTypeName getTypeName(UnqualifiedTypeName typeName) {
    return localTypeNames.get(typeName);
  }
}
