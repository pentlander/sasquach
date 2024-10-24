package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FunctionCall;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.ast.expression.Match;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.ast.typenode.StructTypeNode;
import com.pentlander.sasquach.ast.typenode.SumTypeNode;
import com.pentlander.sasquach.ast.typenode.TupleTypeNode;
import com.pentlander.sasquach.name.UnqualifiedName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;


/**
 * Validates the structure of the abstract syntax tree.
 */
public class AstValidator {
  private final CompilationUnit compilationUnit;

  public AstValidator(CompilationUnit compilationUnit) {
    this.compilationUnit = compilationUnit;
  }

  /**
   * Validate the AST struct.
   *
   * @return list of errors from the AST validation.
   */
  public List<Error> validate() {
    var errors = new ArrayList<Error>();
    for (var module : compilationUnit.modules()) {
      var struct = module.struct();

      for (var typeStatement : struct.typeStatements()) {
        if (!typeStatement.isAlias()) {
          switch (typeStatement.typeNode()) {
            case StructTypeNode _, TupleTypeNode _, SumTypeNode _ -> {}
            default -> {
              errors.add(new BasicError(
                  "Type value must be a struct, tuple, or sum type. Try 'typealias' instead.",
                  typeStatement.typeNode().range()));
            }
          }
        }
      }

      var functions = struct.functions();
      var functionNames = new HashMap<UnqualifiedName, NamedFunction>();

      for (var function : functions) {
        var existingFunction = functionNames.put(function.name(), function);
        // Check if there are multiple functions with the same captureName
        if (existingFunction != null) {
          errors.add(new DuplicationError(
              "Function '%s' already defined in modules '%s'"
                  .formatted(function.name(), module.name()),
              List.of(existingFunction.nameRange(), function.nameRange())));
        }

        errors.addAll(validateFunctionParameters(function.name(), function.parameters()));
        errors.addAll(validateExpression(function.expression()));
      }
    }

    return errors;
  }

  @Nullable
  private BasicError checkForRecur(Expression expr, Range range) {
    if (expr instanceof Block block) {
      expr = block.returnExpression();
    }

    return switch (expr) {
      case IfExpression ifExpr -> {
        var hasRecur = ifExpr.trueExpression() instanceof Recur
            || ifExpr.falseExpression() instanceof Recur;
        yield !hasRecur ? new BasicError("Loop must contain a recur", range) : null;

      }
      case Match match -> {
        var hasRecur = false;
        for (var branch : match.branches()) {
          if (checkForRecur(branch.expr(), range) == null) hasRecur = true;
        }
        yield !hasRecur ? new BasicError("Loop must contain a recur", range) : null;
      }
      default -> new BasicError("Loop must end in an if expression", range);
    };
  }

  private List<Error> validateExpression(Expression expression) {
    var errors = new ArrayList<Error>();
    switch (expression) {
      case Block block -> errors.addAll(validateBlock(block));
      case Loop loop -> {
        var expr = loop.expression();
        var error = checkForRecur(expr, loop.range());
        if (error != null) {
          errors.add(error);
        }
      }
      case FunctionCall funcCall -> {
        boolean hasSeenLabel = false;
        for (var argument : funcCall.arguments()) {
          if (argument.label() != null) hasSeenLabel = true;

          if (hasSeenLabel && argument.label() == null) {
            errors.add(new BasicError(
                "Positional args must come before labeled args",
                argument.range()));
          }
        }
      }
      case null, default -> {
      }
    }

    return errors;
  }

  private List<Error> validateBlock(Block block) {
    var errors = new ArrayList<Error>();
    var variables = new HashMap<UnqualifiedName, List<VariableDeclaration>>();
    for (var expr : block.expressions()) {
      if (expr instanceof VariableDeclaration varDecl) {
        variables.computeIfAbsent(varDecl.name(), k -> new ArrayList<>()).add(varDecl);
      }
    }

    variables.forEach((name, vars) -> {
      if (vars.size() > 1) {
        errors.add(new DuplicationError(
            "Variable '%s' already defined in block".formatted(name),
            vars.stream().map(VariableDeclaration::nameRange).toList()));
      }
    });

    return errors;
  }

  record SizeMismatchError(String message, Range range) implements RangedError {
    @Override
    public String toPrettyString(Source source) {
      return message + "\n" + source.highlight(range);
    }
  }

  private List<Error> validateFunctionParameters(UnqualifiedName funcName,
      List<FunctionParameter> funcParameters) {
    var errors = new ArrayList<Error>();
    var hasSeenLabel = false;
    var labelNamesSeen = new HashMap<UnqualifiedName, FunctionParameter>();
    var paramNamesSeen = new HashMap<UnqualifiedName, FunctionParameter>();
    for (var param : funcParameters) {
      if (param.label() != null) {
        hasSeenLabel = true;

        // Error if duplicate label names
        var seenLabel = labelNamesSeen.put(param.label().name(), param);
        if (seenLabel != null) {
          errors.add(new DuplicationError("Parameter labeled '%s' already defined in function '%s'".formatted(param.name(),
              funcName), List.of(seenLabel.id().range())));
        }
      }

      // Error if positional parameters appear after labeled ones
      if (hasSeenLabel && param.label() == null) {
        errors.add(new BasicError(
            "Positional parameters must come before labeled parameters",
            param.range()));
      }

      // Error if duplicate param names
      var seenParam = paramNamesSeen.put(param.name(), param);
      if (seenParam != null) {
        errors.add(new DuplicationError("Parameter '%s' already defined in function '%s'".formatted(param.name(),
            funcName), List.of(seenParam.id().range())));
      }
    }

    return errors;
  }

  record DuplicationError(String message, List<Range.Single> ranges) implements RangedError {
    @Override
    public Range range() {
      return ranges.getFirst();
    }

    @Override
    public String toPrettyString(Source source) {
      var firstRange = ranges.getFirst();
      var restHighlights = ranges.stream().skip(1)
          .map(range -> source.highlight(range) + " other appearance here")
          .collect(Collectors.joining("\n"));
      return """
          %s
          %s first appearance here
          %s
          """.formatted(message, source.highlight(firstRange), restHighlights);
    }
  }
}
