package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.SourcePath;
import java.util.List;

public record CompilationUnit(SourcePath sourcePath, PackageName packageName, List<ModuleDeclaration> modules) {}
