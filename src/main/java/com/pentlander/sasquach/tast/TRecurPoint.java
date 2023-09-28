package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.tast.expression.TFunction;
import com.pentlander.sasquach.tast.expression.TLoop;

public sealed interface TRecurPoint permits TFunction, TLoop {}
