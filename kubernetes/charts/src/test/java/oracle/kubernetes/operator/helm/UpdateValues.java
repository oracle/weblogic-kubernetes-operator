package oracle.kubernetes.operator.helm;

import java.util.Map;

/** An interface which defines the changes to be made to a values file for testing. */
interface UpdateValues {
  void update(Map<String, String> values);
}
