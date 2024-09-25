package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.Position;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.TypeId;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.parser.SasquachParser.ForeignNameContext;
import com.pentlander.sasquach.parser.SasquachParser.FunctionParameterListContext;
import com.pentlander.sasquach.parser.SasquachParser.LabelContext;
import com.pentlander.sasquach.parser.SasquachParser.TypeAnnotationContext;
import com.pentlander.sasquach.parser.SasquachParser.TypeContext;
import com.pentlander.sasquach.parser.SasquachParser.TypeIdentifierContext;
import com.pentlander.sasquach.parser.SasquachParser.TypeParameterListContext;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

interface VisitorHelper {
  static TypeNode typeNode(TypeContext ctx, TypeVisitor visitor) {
    return ctx.accept(visitor);
  }

  SourcePath sourcePath();

  default Range rangeFrom(ParserRuleContext context) {
    Token start = context.getStart();
    Token end = context.getStop();
    var pos = new Position(start.getLine(), start.getCharPositionInLine());
    if (start.getLine() == end.getLine()) {
      return new Range.Single(sourcePath(),
          pos,
          end.getCharPositionInLine() - start.getCharPositionInLine() + 1);
    }
    return new Range.Multi(sourcePath(),
        pos,
        new Position(end.getLine(), end.getCharPositionInLine() + end.getText().length()));
  }

  default Range.Single rangeFrom(Token token) {
    return new Range.Single(sourcePath(),
        new Position(token.getLine(), token.getCharPositionInLine()),
        token.getText().length());
  }

  default Range.Single rangeFrom(TerminalNode node) {
    return rangeFrom(node.getSymbol());
  }

  default Id id(TerminalNode node) {
    return new Id(new UnqualifiedName(node.getText()), rangeFrom(node));
  }

  default TypeId typeId(TypeIdentifierContext ctx) {
    var node = ctx.ID();
    var name = new UnqualifiedTypeName(node.getText());
    return new TypeId(name, rangeFrom(node));
  }

  default TypeId typeId(ForeignNameContext ctx) {
    var node = ctx.ID();
    return new TypeId(new UnqualifiedTypeName(node.getText()), rangeFrom(node));
  }

  default List<TypeParameter> typeParams(TypeParameterListContext ctx) {
    return Optional.ofNullable(ctx)
        .map(TypeParameterListContext::typeIdentifier)
        .orElse(List.of())
        .stream()
        .map(typeParamCtx -> new TypeParameter(typeId(typeParamCtx)))
        .toList();
  }

  default @Nullable Id label(@Nullable LabelContext labelCtx) {
    return labelCtx != null ? id(labelCtx.ID()) : null;
  }

  static TypeNode typeAnnotation(TypeAnnotationContext ctx, TypeVisitor visitor) {
    if (ctx == null) return null;
    return typeNode(ctx.type(), visitor);
  }

  default List<FunctionParameter> parameterList(TypeVisitor typeVisitor, ExpressionVisitor exprVisitor,
      FunctionParameterListContext ctx) {
    var params = new ArrayList<FunctionParameter>();
    for (var paramCtx : ctx.functionParameter()) {
      var type = VisitorHelper.typeAnnotation(paramCtx.typeAnnotation(), typeVisitor);
      var label = label(paramCtx.label());
      var defaultExpr =
          paramCtx.expression() != null ? paramCtx.expression().accept(exprVisitor)
              : null;
      var param = new FunctionParameter(id(paramCtx.ID()), label, type, defaultExpr);
      params.add(param);
    }
    return params;
  }
}
