package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.SourcePath;
import java.util.List;

public record TCompilationUnit(SourcePath sourcePath, PackageName packageName,
                               List<TModuleDeclaration> modules) {}
