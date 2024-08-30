package com.pentlander.sasquach.runtime;

import com.pentlander.sasquach.runtime.StructLinker.StructCallSiteDesc;
import java.util.Arrays;
import java.util.List;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.Namespace;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.linker.LinkRequest;

record StructLinkRequest(LinkRequest linkRequest, Operation baseOperation,
                         List<Namespace> namespaces, Object name, boolean shouldHandle) implements LinkRequest {
  public static StructLinkRequest from(LinkRequest linkRequest, boolean shouldHandle) {
    if (linkRequest instanceof StructLinkRequest structLinkReq) {
      if (structLinkReq.shouldHandle == shouldHandle) {
        return structLinkReq;
      }
      return new StructLinkRequest(structLinkReq.linkRequest, structLinkReq.baseOperation, structLinkReq.namespaces, structLinkReq.name, shouldHandle);
    }

    var namedOp = linkRequest.getCallSiteDescriptor().getOperation();
    var name = NamedOperation.getName(namedOp);
    var namespaceOp = NamedOperation.getBaseOperation(namedOp);
    var baseOperation = NamespaceOperation.getBaseOperation(namespaceOp);
    var namespaces = Arrays.asList(NamespaceOperation.getNamespaces(namespaceOp));
    return new StructLinkRequest(linkRequest, baseOperation, namespaces, name, shouldHandle);
  }

  @Override
  public CallSiteDescriptor getCallSiteDescriptor() {
    return linkRequest.getCallSiteDescriptor();
  }

  @Override
  public Object[] getArguments() {
    return linkRequest.getArguments();
  }

  @Override
  public Object getReceiver() {
    return linkRequest.getReceiver();
  }

  @Override
  public boolean isCallSiteUnstable() {
    return linkRequest.isCallSiteUnstable();
  }

  @Override
  public LinkRequest replaceArguments(CallSiteDescriptor callSiteDescriptor, Object... arguments) {
    return new StructLinkRequest(
        linkRequest.replaceArguments(callSiteDescriptor, arguments),
        baseOperation,
        namespaces,
        name,
        shouldHandle);
  }
}
