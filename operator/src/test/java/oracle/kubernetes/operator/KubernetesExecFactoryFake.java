// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.utils.KubernetesExec;
import oracle.kubernetes.operator.utils.KubernetesExecFactory;
import org.jetbrains.annotations.NotNull;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;

class KubernetesExecFactoryFake implements KubernetesExecFactory {
  private final Map<String, String> responses = new HashMap<>();

  @NotNull
  public Memento install() throws NoSuchFieldException {
    return StaticStubSupport.install(ServerStatusReader.class, "EXEC_FACTORY", this);
  }

  void defineResponse(String serverName, String response) {
    responses.put(LegalNames.toPodName(UID, serverName), response);
  }

  @Override
  public KubernetesExec create(ApiClient client, V1Pod pod, String containerName) {
    return new KubernetesExec() {
      @Override
      public Process exec(String... command) {
        return createStub(ServerStatusReaderTest.ProcessStub.class, getResponse(pod.getMetadata().getName()));
      }

      private String getResponse(String name) {
        return Optional.ofNullable(responses.get(name)).orElse("** unknown pod **");
      }
    };
  }
}
