// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateRunning;
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;

import static oracle.kubernetes.operator.LabelConstants.JOBNAME_LABEL;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONTAINER_NAME;

public class FluentdUtils {

  /**
   * define the introspector job pod container status.
   * @param jobPod jobPad object
   * @param jobName job name
   * @param terminateJobContainer set introspector container with terminate status if true, otherwise running.
   * @param terminateFluentd set inrospector fluentd container with terminate status if true, otherwise running.
   */
  public static void defineFluentdJobContainersCompleteStatus(
      V1Pod jobPod, String jobName, boolean terminateJobContainer, boolean terminateFluentd) {
    Map<String, String> labels = new HashMap<>();
    labels.put(JOBNAME_LABEL, jobName);
    jobPod.getMetadata().setLabels(labels);
    jobPod.spec(new V1PodSpec());
    jobPod.getSpec().addContainersItem(new V1Container().name(FLUENTD_CONTAINER_NAME));
    jobPod.getSpec().addContainersItem(new V1Container().name(jobName));
    V1PodStatus podStatus = new V1PodStatus();
    V1ContainerState jobContainerState = new V1ContainerState();
    if (terminateJobContainer) {
      jobContainerState.setTerminated(new V1ContainerStateTerminated().exitCode(0));
    } else {
      jobContainerState.setRunning(new V1ContainerStateRunning());
    }
    podStatus.addContainerStatusesItem(new V1ContainerStatus()
            .name(jobName).state(jobContainerState));

    V1ContainerState fluentdContainerState = new V1ContainerState();
    if (terminateFluentd) {
      fluentdContainerState.setTerminated(new V1ContainerStateTerminated().exitCode(1));
    } else {
      fluentdContainerState.setRunning(new V1ContainerStateRunning());
    }
    podStatus.addContainerStatusesItem(new V1ContainerStatus()
            .name(FLUENTD_CONTAINER_NAME).state(fluentdContainerState));

    jobPod.setStatus(podStatus);

  }
}
