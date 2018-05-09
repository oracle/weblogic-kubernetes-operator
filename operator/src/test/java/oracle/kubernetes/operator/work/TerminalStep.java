package oracle.kubernetes.operator.work;

/** A do-nothing step that can be used as a base for testing. It has no next step. */
public class TerminalStep extends Step {
  public TerminalStep() {
    super(null);
  }

  @Override
  public NextAction apply(Packet packet) {
    return doNext(packet);
  }
}
