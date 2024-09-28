package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.parser.SasquachParser.CompilationUnitContext;
import com.pentlander.sasquach.parser.SasquachParser.ModuleDeclarationContext;
import java.util.ArrayList;

public class CompilationUnitVisitor extends
    com.pentlander.sasquach.parser.SasquachBaseVisitor<CompileResult<CompilationUnit>> {
  private final SourcePath sourcePath;
  private final PackageName packageName;

  public CompilationUnitVisitor(SourcePath sourcePath, PackageName packageName) {
    this.packageName = packageName;
    this.sourcePath = sourcePath;
  }

  @Override
  public CompileResult<CompilationUnit> visitCompilationUnit(CompilationUnitContext ctx) {
    var modules = new ArrayList<ModuleDeclaration>();
    var errors = RangedErrorList.builder();
    for (ModuleDeclarationContext moduleDecl : ctx.moduleDeclaration()) {
      var result = moduleDecl.accept(new ModuleVisitor(
          sourcePath,
          packageName));
      modules.add(result.item());
      errors.addAll(result.errors());
    }

    return CompileResult.of(new CompilationUnit(sourcePath, packageName, modules), errors.build());
  }
}
