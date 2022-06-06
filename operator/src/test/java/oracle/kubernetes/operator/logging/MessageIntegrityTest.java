// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import oracle.kubernetes.common.logging.MessageKeys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

class MessageIntegrityTest {

  private static final DuplicateFindingProperties properties = new DuplicateFindingProperties();

  @BeforeAll
  static void setUpClass() throws IOException {
    properties.load(MessageIntegrityTest.class.getClassLoader().getResourceAsStream("Operator.properties"));
  }

  @Test
  void operatorPropertiesHasNoDuplications() throws IOException {
    assertThat(properties.duplicateProperties, empty());
  }

  @Test
  void messageKeysAllHaveProperties() throws IllegalAccessException {
    List<String> missingProperties = new ArrayList<>();
    for (Field field : MessageKeys.class.getDeclaredFields()) {
      if (field.getType().equals(String.class) && !(properties.containsKey(field.get(null)))) {
        missingProperties.add(field.getName());
      }
    }
    assertThat("MessageKey entries with no properties", missingProperties, empty());
  }

  static class DuplicateFindingProperties extends Properties {
    private final List<Object> duplicateProperties = new ArrayList<>();

    @Override
    public synchronized Object put(Object key, Object value) {
      if (containsKey(key)) {
        return duplicateProperties.add(key);
      } else {
        return super.put(key, value);
      }
    }
  }
}
