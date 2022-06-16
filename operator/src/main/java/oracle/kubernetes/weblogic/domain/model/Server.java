// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.operator.ServerStartPolicy;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Server extends BaseConfiguration {

  /**
   * Tells the operator whether the customer wants the server to be running. For clustered servers -
   * the operator will start it if the policy is Always or the policy is IfNeeded and the server
   * needs to be started to get to the cluster's replica count.
   *
   * @since 2.0
   */
  @EnumClass(value = ServerStartPolicy.class, qualifier = "forServer")
  @Description("The strategy for deciding whether to start a WebLogic Server instance. "
      + "Legal values are Always, Never, or IfNeeded. Defaults to IfNeeded. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-lifecycle/startup/#starting-and-stopping-servers.")
  @Default(strDefault = "IfNeeded")
  private ServerStartPolicy serverStartPolicy;

  protected Server getConfiguration() {
    Server configuration = new Server();
    configuration.fillInFrom(this);
    configuration.setRestartVersion(this.getRestartVersion());
    return configuration;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append(serverStartPolicy)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    if (!(o instanceof Server)) {
      return false;
    }

    Server that = (Server) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(serverStartPolicy, that.serverStartPolicy)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(serverStartPolicy)
        .toHashCode();
  }

  @Override
  public ServerStartPolicy getServerStartPolicy() {
    return serverStartPolicy;
  }

  @Override
  public void setServerStartPolicy(ServerStartPolicy serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }
}
