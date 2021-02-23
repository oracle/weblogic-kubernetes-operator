// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import javax.annotation.Nonnull;

/**
 * Constants for generating introspector config maps.
 */
public interface IntrospectorConfigMapConstants {

  /** The topology generated from the WebLogic domain. */
  String TOPOLOGY_YAML = "topology.yaml";

  /** An MD5 has of the Model-in-Image secrets. */
  String SECRETS_MD_5 = "secrets.md5";

  /** A hash computed from the WebLogic domain. */
  String DOMAINZIP_HASH = "domainzip_hash";

  /** The last value of the restartVersion field from the domain resource. */
  String DOMAIN_RESTART_VERSION = "weblogic.domainRestartVersion";

  /** A hash of the Model-in-Image inputs. */
  String DOMAIN_INPUTS_HASH = "weblogic.domainInputsHash";

  /** The number of config maps required to hold the encoded domains. */
  String NUM_CONFIG_MAPS = "numConfigMaps";

  /**
   * The prefix for a number of keys which may appear in the introspector config map.
   * They are not preserved from one update to another.
   */
  String SIT_CONFIG_FILE_PREFIX = "Sit-Cfg";

  /**  The suffix for naming introspector config maps. */
  String INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX = "-weblogic-domain-introspect-cm";

  /**
   * Returns the name of an introspector config map.
   * @param domainUid the unique UID for the domain containing the map
   * @param index the index of the config map
   */
  static String getIntrospectorConfigMapName(String domainUid, int index) {
    return getIntrospectorConfigMapNamePrefix(domainUid) + suffix(index);
  }

  static String getIntrospectorConfigMapNamePrefix(String uid) {
    return uid + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
  }

  /**  A (possibly empty) suffix for introspector config maps. */
  static String suffix(int index) {
    return index == 0 ? "" : "_" + index;
  }

  /**
   * Returns the mount path for an introspector volume mount.
   * @param index the index of the mount / config map
   */
  @Nonnull
  static String getIntrospectorVolumePath(int index) {
    return "/weblogic-operator/introspector" + suffix(index);
  }
}
