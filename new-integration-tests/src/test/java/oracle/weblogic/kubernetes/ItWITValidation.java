// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.actions.TestActions.withWITParams;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Simple validation of basic WIT functions")
class ItWITValidation implements LoggedTest {
  @Test
  @DisplayName("Create a MII image")
  public void testCreatingMIIImage() {

    // TODO the installWIT and installWDT calls below need to be moved to 
    // an action that does all the prerequisites once we have that action.
  
    // install WIT using the default version and location
    boolean downloadWIT = TestActions.installWIT();
   
    assertEquals(true, downloadWIT, "Failed to download or unzip WebLogic Image Tool");
    
    // install WDT using the default version and location
    boolean downloadWDT = TestActions.installWDT();

    assertEquals(true, downloadWDT, "Failed to download WebLogic Deploy Tool");

    // create the MII image
    // TODO add model files and other contents to the image once we have those resources
    boolean success = TestActions.createMIIImage(withWITParams());

    assertEquals(true, success, "Failed to create the image using WebLogic Deploy Tool");
  } 
}
