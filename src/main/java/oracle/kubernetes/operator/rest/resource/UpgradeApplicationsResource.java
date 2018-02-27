// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.rest.resource;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.UpgradeApplicationModel;
import oracle.kubernetes.operator.rest.model.UpgradeApplicationsModel;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UpgradeApplicationsResource is a jaxrs resource that implements the REST api for the
 * /operator/{version}/domains/{domainUID}/upgradeApplications path.
 * It can be used to upgrade a list of Java EE applications in a WebLogic domain.
 */

public class UpgradeApplicationsResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Construct a UpgradeApplicationsResource.
   * @param parent - the jaxrs resource that parents this resource.
   * @param pathSegment - the last path segment in the url to this resource.
   */
  public UpgradeApplicationsResource(BaseResource parent, String pathSegment) {
    super(parent, pathSegment);
  }

  /**
   * Upgrade a list of Java EE applications deployed to Weblogic domain.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public void post(UpgradeApplicationsModel params) {
    LOGGER.entering(href());

    // A map containing the info of the app to be patched: {name: [patchedLocation, backupLocation]}
    Map<String, List<String>> appsInfoMap = new HashMap<String, List<String>>();

    for (UpgradeApplicationModel m : params.getApplications()) {
      List<String> locations = new ArrayList<>();
      locations.add(m.getPatchedLocation());
      locations.add(m.getBackupLocation());
      appsInfoMap.put(m.getApplicationName(), locations);

    }

    //getBackend().upgradeApplications(getDomainUid(),appsInfoMap);

    getBackend().upgradeApplications(getDomainUid(),params);

  }

  private String getDomainUid() {
    return getParent().getPathSegment();
  }

}
