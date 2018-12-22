package oracle.kubernetes.weblogic.domain.v2;

import oracle.kubernetes.operator.ServerStartPolicy;

public interface ConfigurationConstants {
  String START_ADMIN_ONLY = ServerStartPolicy.ADMIN_ONLY.name();
  String START_NEVER = ServerStartPolicy.NEVER.name();
  String START_ALWAYS = ServerStartPolicy.ALWAYS.name();
  String START_IF_NEEDED = ServerStartPolicy.IF_NEEDED.name();
}
