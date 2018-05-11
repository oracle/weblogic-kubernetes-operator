// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1Ingress;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.watcher.WatchListener;

/**
 * This class handles Ingress watching. It receives Ingress change events and sends them into the
 * operator for processing.
 */
public class IngressWatcher extends Watcher<V1beta1Ingress> {
  private final String ns;

  public static IngressWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchListener<V1beta1Ingress> listener,
      AtomicBoolean isStopping) {
    IngressWatcher watcher = new IngressWatcher(ns, initialResourceVersion, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  private IngressWatcher(
      String ns,
      String initialResourceVersion,
      WatchListener<V1beta1Ingress> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, isStopping, listener);
    this.ns = ns;
  }

  @Override
  public WatchI<V1beta1Ingress> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createIngressWatch(ns);
  }

  static String getIngressDomainUID(V1beta1Ingress ingress) {
    V1ObjectMeta meta = ingress.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.DOMAINUID_LABEL);
    }
    return null;
  }

  static String getIngressClusterName(V1beta1Ingress ingress) {
    V1ObjectMeta meta = ingress.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.CLUSTERNAME_LABEL);
    }
    return null;
  }
}
