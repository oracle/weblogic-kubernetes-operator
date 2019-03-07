package oracle.kubernetes.operator;

public interface OperatorConstants {
  static final String OPERATOR_DIR = "/operator/";
  static final String INTERNAL_REST_IDENTITY_DIR = OPERATOR_DIR + "internal-identity/";
  static final String INTERNAL_CERTIFICATE = INTERNAL_REST_IDENTITY_DIR + "internalOperatorCert";
}
