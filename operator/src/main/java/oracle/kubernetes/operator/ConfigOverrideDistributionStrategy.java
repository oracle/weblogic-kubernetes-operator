// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public enum ConfigOverrideDistributionStrategy {
  DYNAMIC, ROLLING;

  public static final ConfigOverrideDistributionStrategy DEFAULT = DYNAMIC;
}


/*
1. Must record old values of configOverrides and configOverride secrets in the situ config map.
Add a step to validate it with a map of added values
2. Post-introspect step compares values? Or compare on domain change? If different,
add value to packet to indicate need to change
3. PodHelper for managed servers detects that and either adds pods to rolling list or initiates parallel calls to
step and packet that invoke the copy script
4. those field changes should run the introspector but not roll the pods



    on make right domain... compare new to cached. If different, add OVERRIDES_MODIFIED to packet and add step to
    update values
    managed pod helper: if OVERRIDES_MODIFIED found, do copy update/roll


    instead of add fields step... step to compare config* fields to current map if any,
    adding them
    if different, add OVERRIDES_MODIFIED to packet.

 */