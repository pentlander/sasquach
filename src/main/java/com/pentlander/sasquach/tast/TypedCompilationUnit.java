package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.SourcePath;
import java.util.List;

public record TypedCompilationUnit(SourcePath sourcePath, String packageName,
                                   List<TypedModuleDeclaration> modules) {}
