package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.ast.id.QualifiedModuleId;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.parser.SasquachParser.ModuleDeclarationContext;

class ModuleVisitor extends
    com.pentlander.sasquach.parser.SasquachBaseVisitor<CompileResult<ModuleDeclaration>> implements
    RangeHelper {
  private final SourcePath sourcePath;
  private final PackageName packageName;

  ModuleVisitor(SourcePath sourcePath, PackageName packageName) {
    this.sourcePath = sourcePath;
    this.packageName = packageName;
  }

  @Override
  public CompileResult<ModuleDeclaration> visitModuleDeclaration(ModuleDeclarationContext ctx) {
    var name = new QualifiedModuleName(packageName, ctx.moduleName().getText());
    var moduleCtx = new ModuleContext(name, sourcePath);
    var structVisitor = new StructVisitor(moduleCtx, new StructIdentifier.ModuleName(name));
    var struct = (ModuleStruct) ctx.struct().accept(structVisitor);
    var modDecl = new ModuleDeclaration(new QualifiedModuleId(name,
        rangeFrom(ctx.moduleName().ID())), struct, rangeFrom(ctx));
    return CompileResult.of(modDecl, moduleCtx.errors());
  }

  @Override
  public SourcePath sourcePath() {
    return sourcePath;
  }
}
