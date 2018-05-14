package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ServerDownFinalizeStep extends Step {
  private final String serverName;

  public ServerDownFinalizeStep(String serverName, Step next) {
    super(next);
    this.serverName = serverName;
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
    info.getServers().remove(serverName);
    return doNext(getNext(), packet);
  }
}
