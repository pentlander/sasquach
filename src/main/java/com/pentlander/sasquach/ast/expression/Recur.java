package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Argument;
import java.util.List;

public record Recur(List<Argument> arguments, Range range) implements Expression {}
