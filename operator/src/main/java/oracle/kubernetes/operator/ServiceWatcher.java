// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.watcher.WatchListener;

/**
 * This class handles Service watching. It receives service change events and sends them into the
 * operator for processing.
 */
public class ServiceWatcher extends Watcher<V1Service> {
  private final String ns;

  public static ServiceWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchListener<V1Service> listener,
      AtomicBoolean isStopping) {
    ServiceWatcher watcher = new ServiceWatcher(ns, initialResourceVersion, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  private ServiceWatcher(
      String ns,
      String initialResourceVersion,
      WatchListener<V1Service> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, isStopping, listener);
    this.ns = ns;
  }

  @Override
  public WatchI<V1Service> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createServiceWatch(ns);
  }

  static String getServiceDomainUID(V1Service service) {
    V1ObjectMeta meta = service.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.DOMAINUID_LABEL);
    }
    return null;
  }

  static String getServiceServerName(V1Service service) {
    V1ObjectMeta meta = service.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.SERVERNAME_LABEL);
    }
    return null;
  }

  static String getServiceChannelName(V1Service service) {
    V1ObjectMeta meta = service.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.CHANNELNAME_LABEL);
    }
    return null;
  }
}
