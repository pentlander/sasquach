package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.SourcePath;
import java.util.List;

public record TCompilationUnit(SourcePath sourcePath, String packageName,
                               List<TModuleDeclaration> modules) {}
