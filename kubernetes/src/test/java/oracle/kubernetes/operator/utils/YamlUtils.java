// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

/** Yaml utilities for the create script tests. */
public class YamlUtils {

  /**
   * Create Yaml instance.
   * @return Yaml instance
   */
  public static Yaml newYaml() {
    // always make a new yaml object since it appears to be stateful
    // so there are problems if you try to use the same one to
    // parse different yamls at the same time
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    return new Yaml(new MyConstructor(), new MyRepresenter(), options);
  }

  // Note: don't name it 'equalTo' since it conflicts with static importing
  // all the standard matchers, which would force callers to individually import
  // the standard matchers.
  public static YamlMatcher yamlEqualTo(Object expectedObject) {
    return new YamlMatcher(expectedObject);
  }

  // We want to be able to test that yamls are identical by doing string compares
  // over the entire yaml.  This doesn't work out of the box since the order of
  // mapping properties is not specified.  So, sort them ...
  //
  // Some kubernetes client classes aren't snakeyaml dump friendly.
  // This class works around these issues too.
  private static class MyRepresenter extends Representer {

    private MyRepresenter() {
      super();
      representers.put(IntOrString.class, new RepresentIntOrString());
    }

    @Override
    protected Node representMapping(Tag tag, Map<?, ?> mapping, DumperOptions.FlowStyle flowStyle) {
      Map<?, ?> sortedMapping = new TreeMap<>(mapping);
      return super.representMapping(tag, sortedMapping, flowStyle);
    }

    private class RepresentIntOrString implements Represent {
      public Node representData(Object data) {
        IntOrString val = (IntOrString) data;
        if (val.isInteger()) {
          return representScalar(Tag.INT, "" + val.getIntValue(), null);
        } else {
          return representScalar(Tag.STR, val.getStrValue(), null);
        }
      }
    }
  }

  // Some kubernetes client classes aren't snakeyaml load friendly.
  // This class works around these issues.
  private static class MyConstructor extends Constructor {
    private MyConstructor() {
      super();
      yamlClassConstructors.put(NodeId.scalar, new WorkAroundConstructScalar());
    }

    private class WorkAroundConstructScalar extends Constructor.ConstructScalar {
      public Object construct(Node node) {
        Class<?> type = node.getType();
        if (IntOrString.class.equals(type)) {
          ScalarNode sn = (ScalarNode) node;
          Tag tag = sn.getTag();
          String value = sn.getValue();
          if (Tag.STR.equals(tag)) {
            return KubernetesArtifactUtils.newIntOrString(value);
          } else if (Tag.INT.equals(tag)) {
            return KubernetesArtifactUtils.newIntOrString(Integer.parseInt(value));
          }
        } else if (Quantity.class.equals(type)) {
          ScalarNode sn = (ScalarNode) node;
          return KubernetesArtifactUtils.newQuantity(sn.getValue());
        }
        return super.construct(node);
      }
    }
  }

  // Most k8s objects have an 'equals' implementation that works well across instances.
  // A few of the, e.g. V1 Secrets which prints out secrets as byte array addresses, don't.
  // For there kinds of objects, you can to convert them to yaml strings then comare those.
  // Anyway, it doesn't hurt to always just convert to yaml and compare the strings so that
  // we don't have to write type-dependent code.
  private static class YamlMatcher extends TypeSafeDiagnosingMatcher<Object> {
    private String expectedString;

    private YamlMatcher(Object expectedObject) {
      expectedString = objectToYaml(expectedObject);
    }

    @Override
    protected boolean matchesSafely(Object returnedObject, Description description) {
      String returnedString = objectToYaml(returnedObject);
      if (Objects.equals(expectedString, returnedString)) {
        return true;
      }

      int index = indexOfDifference(expectedString, returnedString);
      if (index < 10) {
        description.appendText("\nwas\n").appendText(returnedString);
      } else {
        description
            .appendText("\ndiffers at position ")
            .appendValue(index)
            .appendText(toDifference(index, returnedString));
      }
      return false;
    }

    private int indexOfDifference(final CharSequence cs1, final CharSequence cs2) {
      if (cs1 == cs2) {
        return -1;
      }
      if (cs1 == null || cs2 == null) {
        return 0;
      }
      int i;
      for (i = 0; i < cs1.length() && i < cs2.length(); ++i) {
        if (cs1.charAt(i) != cs2.charAt(i)) {
          break;
        }
      }
      if (i < cs2.length() || i < cs1.length()) {
        return i;
      }
      return -1;
    }

    private String toDifference(int diffIndex, String returnedString) {
      StringBuilder sb = new StringBuilder(":\n");
      int firstDiffLineIndex =
          Math.max(0, returnedString.substring(0, diffIndex).lastIndexOf('\n'));
      int lastMatchLineIndex =
          Math.max(0, returnedString.substring(0, firstDiffLineIndex).lastIndexOf('\n'));
      sb.append(returnedString, lastMatchLineIndex, firstDiffLineIndex);
      if (sb.length() > 0) {
        sb.append("\n-----\n");
      }
      sb.append(returnedString, firstDiffLineIndex, firstDiffLineIndex + 100);
      return sb.toString();
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("\n").appendText(expectedString);
    }

    private String objectToYaml(Object object) {
      return YamlUtils.newYaml().dump(object);
    }
  }
}
