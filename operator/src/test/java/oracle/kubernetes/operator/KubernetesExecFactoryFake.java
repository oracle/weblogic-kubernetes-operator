// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.utils.KubernetesExec;
import oracle.kubernetes.operator.utils.KubernetesExecFactory;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;

class KubernetesExecFactoryFake implements KubernetesExecFactory {
  private final Map<String, String> responses = new HashMap<>();
  private final Map<String, Integer> exitCodes = new HashMap<>();

  @Nonnull
  public Memento install() throws NoSuchFieldException {
    return StaticStubSupport.install(ServerStatusReader.class, "execFactory", this);
  }

  void defineResponse(String serverName, String response) {
    defineResponse(serverName, response, 0);
  }

  void defineResponse(String serverName, String response, Integer exitCode) {
    responses.put(LegalNames.toPodName(UID, serverName), response);
    exitCodes.put(LegalNames.toPodName(UID, serverName), exitCode);
  }

  @Override
  public KubernetesExec create(ApiClient client, V1Pod pod, String containerName) {
    return new KubernetesExec() {
      @Override
      public Process exec(String... command) {
        return createStub(ServerStatusReaderTest.ProcessStub.class, getResponse(pod.getMetadata().getName()),
            getExitCode(pod.getMetadata().getName()));
      }

      private String getResponse(String name) {
        return Optional.ofNullable(responses.get(name)).orElse("** unknown pod **");
      }

      private Integer getExitCode(String name) {
        return Optional.ofNullable(exitCodes.get(name)).orElse(0);
      }
    };
  }
}
