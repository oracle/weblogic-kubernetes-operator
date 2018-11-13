// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;

public class ExternalAdminChannelsStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public ExternalAdminChannelsStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

    Collection<NetworkAccessPoint> validChannels =
        adminChannelsToCreate(info.getScan(), info.getDomain());
    if (validChannels != null && !validChannels.isEmpty()) {
      return doNext(new ExternalAdminChannelIteratorStep(info, validChannels, getNext()), packet);
    }

    return doNext(packet);
  }

  /**
   * This method checks the domain spec against any configured network access points defined for the
   * domain. This implementation only handles T3 protocol for externalization.
   *
   * @param scan WlsDomainConfig from discovery containing configuration
   * @param dom Domain containing Domain resource information
   * @return Validated collection of network access points
   */
  public static Collection<NetworkAccessPoint> adminChannelsToCreate(
      WlsDomainConfig scan, Domain dom) {
    LOGGER.entering();

    // The following hard-coded values for the nodePort min/max ranges are
    // provided here until the appropriate API is discovered to obtain
    // this information from Kubernetes.
    Integer nodePortMin = 30000;
    Integer nodePortMax = 32767;

    WlsServerConfig adminServerConfig = scan.getServerConfig(dom.getAsName());

    List<NetworkAccessPoint> naps = adminServerConfig.getNetworkAccessPoints();
    // This will become a list of valid channels to create services for.
    Collection<NetworkAccessPoint> channels = new ArrayList<>();

    // Pick out externalized channels from the server channels list
    for (String incomingChannel : dom.getExportedNetworkAccessPointNames()) {
      boolean missingChannel = true;
      for (NetworkAccessPoint nap : naps) {
        if (nap.getName().equalsIgnoreCase(incomingChannel)) {
          missingChannel = false;
          channels.add(nap);
          break;
        }
      }
      if (missingChannel) {
        LOGGER.warning(MessageKeys.EXCH_CHANNEL_NOT_DEFINED, incomingChannel, dom.getAsName());
      }
    }

    // Iterate through the selected channels and validate
    Collection<NetworkAccessPoint> validatedChannels = new ArrayList<>();
    for (NetworkAccessPoint nap : channels) {

      // Only supporting T3 for now.
      if (!nap.getProtocol().equalsIgnoreCase("t3")) {
        LOGGER.severe(MessageKeys.EXCH_WRONG_PROTOCOL, nap.getName(), nap.getProtocol());
        continue;
      }

      // Until otherwise determined, ports must be the same.
      if (!nap.getListenPort().equals(nap.getPublicPort())) {
        // log a warning and ignore this item.
        LOGGER.warning(MessageKeys.EXCH_UNEQUAL_LISTEN_PORTS, nap.getName());
        continue;
      }

      // Make sure configured port is within NodePort range.
      if (nap.getListenPort().compareTo(nodePortMin) < 0
          || nap.getListenPort().compareTo(nodePortMax) > 0) {
        // port setting is outside the NodePort range limits
        LOGGER.warning(
            MessageKeys.EXCH_OUTSIDE_RANGE,
            nap.getName(),
            nap.getPublicPort(),
            nodePortMin,
            nodePortMax);
        continue;
      }

      validatedChannels.add(nap);
    }

    LOGGER.exiting();
    return validatedChannels;
  }
}
