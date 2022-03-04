package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.Loop;

public sealed interface RecurPoint permits Function, Loop {}
