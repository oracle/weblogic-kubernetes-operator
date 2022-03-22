// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.stream.Stream;

import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAGS;

public class Params {

  /**
   * This method is used in parameterized test to get the WebLogic image tags as
   * values.
   * @return stream of WebLogic image tag values
   */
  public static Stream<String> webLogicImageTags() {
    return Stream.of(WEBLOGIC_IMAGE_TAGS.split(","));
  }
}
