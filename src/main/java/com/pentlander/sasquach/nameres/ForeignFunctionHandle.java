package com.pentlander.sasquach.nameres;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.reflect.Executable;

public record ForeignFunctionHandle(DirectMethodHandleDesc methodHandleDesc, Executable executable) {}
