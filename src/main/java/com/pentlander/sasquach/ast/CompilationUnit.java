package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.SourcePath;
import java.util.List;

public record CompilationUnit(SourcePath sourcePath, String packageName, List<ModuleDeclaration> modules) {}
