package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.InvocationKind;
import com.pentlander.sasquach.type.Type;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Executable;
import java.util.List;

public record ForeignFunctionHandle(MethodHandle methodHandle, InvocationKind invocationKind,
                                    Executable executable) {}
