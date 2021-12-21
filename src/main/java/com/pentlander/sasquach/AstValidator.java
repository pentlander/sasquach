package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;


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
      List<Function> functions = module.struct().functions();
      var functionNames = new HashMap<String, Function>();

      for (Function function : functions) {
        Function existingFunction = functionNames.put(function.name(), function);
        // Check if there are multiple functions with the same name
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

  private List<Error> validateExpression(Expression expression) {
    var errors = new ArrayList<Error>();
    if (expression instanceof Block block) {
      errors.addAll(validateBlock(block));
    }

    return errors;
  }

  private List<Error> validateBlock(Block block) {
    var errors = new ArrayList<Error>();
    var variables = new HashMap<String, List<VariableDeclaration>>();
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

  private List<Error> validateFunctionParameters(String funcName,
      List<FunctionParameter> funcParameters) {
    var paramNames = funcParameters.stream()
        .collect(Collectors.groupingBy(FunctionParameter::name));
    var errors = new ArrayList<Error>();
    paramNames.forEach((name, params) -> {
      if (params.size() > 1) {
        errors.add(new DuplicationError(
            "Parameter '%s' already defined in function '%s'".formatted(name, funcName),
            params.stream().map(param -> param.id().range()).toList()));
      }
    });

    return errors;
  }

  record DuplicationError(String message, List<Range.Single> ranges) implements RangedError {
    @Override
    public Range range() {
      return ranges.get(0);
    }

    @Override
    public String toPrettyString(Source source) {
      var firstRange = ranges.get(0);
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
