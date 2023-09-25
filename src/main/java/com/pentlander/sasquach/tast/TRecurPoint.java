package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.tast.expression.TLoop;

public sealed interface TRecurPoint permits Function, TLoop {}
