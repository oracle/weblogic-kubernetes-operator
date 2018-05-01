package oracle.kubernetes.operator.work;

public class InMemoryDatabaseException extends RuntimeException {
  private int code;

  InMemoryDatabaseException(int code, String message) {
    super(message);
    this.code = code;
  }

  public int getCode() {
    return code;
  }
}
