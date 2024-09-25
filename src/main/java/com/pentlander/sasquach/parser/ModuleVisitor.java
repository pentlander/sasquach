package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.parser.SasquachParser.ModuleDeclarationContext;
import com.pentlander.sasquach.parser.Visitor.StructIdentifier;

class ModuleVisitor extends com.pentlander.sasquach.parser.SasquachBaseVisitor<ModuleDeclaration> implements VisitorHelper {
  private final Visitor visitor;
  private final SourcePath sourcePath;
  private final PackageName packageName;

  ModuleVisitor(Visitor visitor, SourcePath sourcePath, PackageName packageName) {
    this.visitor = visitor;
    this.sourcePath = sourcePath;
    this.packageName = packageName;
  }

  @Override
  public ModuleDeclaration visitModuleDeclaration(ModuleDeclarationContext ctx) {
    var name = new QualifiedModuleName(packageName, ctx.moduleName().getText());
    var moduleCtx = new ModuleContext(sourcePath);
    var structVisitor = new StructVisitor(moduleCtx, new StructIdentifier.ModuleName(name));
    var struct = (ModuleStruct) ctx.struct().accept(structVisitor);
    return new ModuleDeclaration(new QualifiedModuleId(name,
        visitor.rangeFrom(ctx.moduleName().ID())), struct, visitor.rangeFrom(ctx));
  }

  @Override
  public SourcePath sourcePath() {
    return sourcePath;
  }
}
