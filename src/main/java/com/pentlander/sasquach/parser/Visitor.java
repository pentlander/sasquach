package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.typenode.NamedTypeNode;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.parser.SasquachParser.CompilationUnitContext;

/**
 * Visitor that parses the source code into an abstract syntax tree.
 */
public class Visitor {
  final SourcePath sourcePath;
  private final PackageName packageName;

  public Visitor(SourcePath sourcePath, PackageName packageName) {
    this.sourcePath = sourcePath;
    this.packageName = packageName;
  }

  public CompilationUnitVisitor compilationUnitVisitor() {
    return new CompilationUnitVisitor();
  }

  public class CompilationUnitVisitor extends
      com.pentlander.sasquach.parser.SasquachBaseVisitor<CompilationUnit> {
    @Override
    public CompilationUnit visitCompilationUnit(CompilationUnitContext ctx) {
      var modules = ctx.moduleDeclaration()
          .stream()
          .map(moduleDecl -> moduleDecl.accept(new ModuleVisitor(
              sourcePath,
              packageName)))
          .toList();
      return new CompilationUnit(sourcePath, packageName, modules);
    }
  }

  sealed interface StructIdentifier {
    None NONE = new None();
    record TypeNode(NamedTypeNode node) implements StructIdentifier {}
    record ModuleName(QualifiedModuleName name) implements StructIdentifier {}

    final class None implements StructIdentifier {
      private None() {}
    }
  }
}
