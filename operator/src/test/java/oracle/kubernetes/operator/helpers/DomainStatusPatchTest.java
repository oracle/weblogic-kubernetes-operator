// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainConditionType;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import oracle.kubernetes.weblogic.domain.model.SubsystemHealth;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.STARTING_STATE;
import static oracle.kubernetes.operator.helpers.DomainStatusPatchTest.OrderedArrayMatcher.hasItemsInOrder;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTERNAL;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainStatusPatchTest {
  private final PatchBuilderStub builder = createStrictStub(PatchBuilderStub.class);

  @Test
  void whenExistingStatusNull_addStatus() {
    DomainStatus status2 = new DomainStatus().withReplicas(2);

    computePatch(null, status2);

    assertThat(builder.getPatches(), hasItemInArray("ADD /status"));
  }

  @Test
  void whenExistingStatusNotNull_dontAddStatus() {
    DomainStatus status1 = new DomainStatus().withReplicas(3);
    DomainStatus status2 = new DomainStatus().withReplicas(2);

    computePatch(status1, status2);

    assertThat(builder.getPatches(), not(hasItemInArray("ADD /status")));
  }

  @Test
  void whenOnlyNewStatusHasReplicas_addIt() {
    DomainStatus status2 = new DomainStatus().withReplicas(2);

    computePatch(null, status2);

    assertThat(builder.getPatches(), hasItemsInOrder("ADD /status", "ADD /status/replicas 2"));
  }

  @Test
  void whenOnlyExistingNewStatusHasReplicas_addIt() {
    DomainStatus status1 = new DomainStatus();
    DomainStatus status2 = new DomainStatus().withReplicas(2);

    computePatch(status1, status2);

    assertThat(builder.getPatches(), hasItemsInOrder("ADD /status/replicas 2"));
  }

  private void computePatch(DomainStatus status1, DomainStatus status2) {
    status2.createPatchFrom(builder, status1);
  }

  @Test
  void whenOnlyOldStatusHasReplicas_removeIt() {
    DomainStatus status1 = new DomainStatus().withReplicas(2);
    DomainStatus status2 = new DomainStatus();

    computePatch(status1, status2);

    assertThat(builder.getPatches(), hasItemInArray("REMOVE /status/replicas"));
  }

  @Test
  void whenBothHaveSameReplicas_ignoreIt() {
    DomainStatus status1 = new DomainStatus().withReplicas(2);
    DomainStatus status2 = new DomainStatus().withReplicas(2);

    computePatch(status1, status2);

    assertThat(builder.getPatches(), arrayWithSize(0));
  }

  @Test
  void whenBothHaveDifferentReplicas_replaceIt() {
    DomainStatus status1 = new DomainStatus().withReplicas(2);
    DomainStatus status2 = new DomainStatus().withReplicas(3);

    computePatch(status1, status2);

    assertThat(builder.getPatches(), hasItemInArray("REPLACE /status/replicas 3"));
  }

  @Test
  void whenOnlyNewHasMessage_addIt() {
    DomainStatus status1 = new DomainStatus();
    DomainStatus status2 = new DomainStatus().withMessage("new and hot");

    computePatch(status1, status2);

    assertThat(builder.getPatches(), hasItemInArray("ADD /status/message 'new and hot'"));
  }

  @Test
  void whenBothHaveDifferentMessages_replaceIt() {
    DomainStatus status1 = new DomainStatus().withMessage("old and broken");
    DomainStatus status2 = new DomainStatus().withMessage("new and hot");

    computePatch(status1, status2);

    assertThat(builder.getPatches(), hasItemInArray("REPLACE /status/message 'new and hot'"));
  }

  @Test
  void whenOnlyOldHasReason_deleteIt() {
    DomainStatus status1 = new DomainStatus().withReason("just because");
    DomainStatus status2 = new DomainStatus();

    computePatch(status1, status2);

    assertThat(builder.getPatches(), hasItemInArray("REMOVE /status/reason"));
  }

  @Test
  void whenOnlyNewStatusHasConditions_addNewConditions() {
    DomainStatus status1 = new DomainStatus();
    DomainStatus status2 = new DomainStatus()
          .addCondition(new DomainCondition(DomainConditionType.FAILED)
                .withReason(INTERNAL).withMessage("hello").withStatus("True"))
          .addCondition(new DomainCondition(DomainConditionType.COMPLETED).withStatus("true"));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder(
                "ADD /status/conditions []",
                "ADD /status/conditions/- {'message':'hello','reason':'Internal','severity':'Severe'"
                      + ",'status':'True','type':'Failed'}",
                "ADD /status/conditions/- {'status':'true','type':'Completed'}"
                ));
  }

  @Test
  void whenOnlyOldStatusHasConditions_removeThem() {
    DomainStatus status1 = new DomainStatus()
          .addCondition(new DomainCondition(DomainConditionType.AVAILABLE)
                .withMessage("hello").withStatus("true"))
          .addCondition(new DomainCondition(DomainConditionType.COMPLETED)
                .withStatus("true"));
    DomainStatus status2 = new DomainStatus();

    computePatch(status1, status2);

    assertThat(builder.getPatches(), hasItemsInOrder("REMOVE /status/conditions/1", "REMOVE /status/conditions/0"));
  }

  @Test
  void whenBothStatusesHaveConditions_replaceMismatches() {
    DomainStatus status1 = new DomainStatus()
          .addCondition(new DomainCondition(DomainConditionType.AVAILABLE).withMessage("hello").withStatus("true"))
          .addCondition(new DomainCondition(DomainConditionType.COMPLETED).withStatus("true"));
    DomainStatus status2 = new DomainStatus()
          .addCondition(new DomainCondition(DomainConditionType.AVAILABLE).withMessage("hello").withStatus("true"))
          .addCondition(new DomainCondition(DomainConditionType.COMPLETED).withMessage("Almost").withStatus("false"));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder("REMOVE /status/conditions/1",
                          "ADD /status/conditions/- {'message':'Almost','status':'false','type':'Completed'}"));
  }

  @Test
  void whenBothStatusesHaveSameConditionTypeWithMismatch_replaceIt() {  // time to rethink this
    DomainStatus status1 = new DomainStatus()
          .addCondition(new DomainCondition(DomainConditionType.COMPLETED));
    DomainStatus status2 = new DomainStatus()
          .addCondition(new DomainCondition(DomainConditionType.COMPLETED).withMessage("Nope").withStatus("False"));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder("REMOVE /status/conditions/0",
                          "ADD /status/conditions/- {'message':'Nope','status':'False','type':'Completed'}"));
  }

  @Test
  void whenOnlyNewStatusHasClusters_addNewClusters() {
    DomainStatus status1 = new DomainStatus();
    DomainStatus status2 = new DomainStatus()
          .addCluster(new ClusterStatus()
              .withClusterName("cluster1").withReplicas(2).withReadyReplicas(4).withObservedGeneration(1L)
              .withMaximumReplicas(10).withReplicasGoal(10).withMinimumReplicas(2))
          .addCluster(new ClusterStatus()
              .withClusterName("cluster2").withReplicas(4).withMaximumReplicas(8).withMinimumReplicas(1));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder(
                "ADD /status/clusters/- {'clusterName':'cluster1','maximumReplicas':10,'minimumReplicas':2,"
                    + "'observedGeneration':1,'readyReplicas':4,'replicas':2,'replicasGoal':10}",
                "ADD /status/clusters/- {'clusterName':'cluster2','maximumReplicas':8,'minimumReplicas':1,"
                    + "'observedGeneration':1,'replicas':4}"
                ));
  }

  @Test
  void whenOnlyOldStatusHasClusters_removeThem() {
    DomainStatus status1 = new DomainStatus()
          .addCluster(new ClusterStatus()
              .withClusterName("cluster1").withReplicas(2).withReadyReplicas(4).withMaximumReplicas(10))
          .addCluster(new ClusterStatus()
              .withClusterName("cluster2").withReplicas(4).withMaximumReplicas(8));
    DomainStatus status2 = new DomainStatus();

    computePatch(status1, status2);

    assertThat(builder.getPatches(), hasItemsInOrder("REMOVE /status/clusters/1", "REMOVE /status/clusters/0"));
  }

  @Test
  void whenBothStatusesHaveClusters_replaceChangedFieldsInMatchingOnes() {
    DomainStatus status1 = new DomainStatus()
          .addCluster(new ClusterStatus()
              .withClusterName("cluster1").withReplicas(2).withReadyReplicas(4).withMaximumReplicas(10)
              .withMinimumReplicas(3))
          .addCluster(new ClusterStatus()
              .withClusterName("cluster2").withReplicas(5).withReadyReplicas(6).withMaximumReplicas(8)
              .withMinimumReplicas(3))
          .addCluster(new ClusterStatus()
              .withClusterName("cluster3").withReplicas(3).withMaximumReplicas(6).withMinimumReplicas(3));
    DomainStatus status2 = new DomainStatus()
          .addCluster(new ClusterStatus()
              .withClusterName("cluster1").withReplicas(2).withReadyReplicas(4).withMaximumReplicas(10)
              .withMinimumReplicas(3))
          .addCluster(new ClusterStatus()
              .withClusterName("cluster2").withReplicas(2).withMaximumReplicas(8).withMinimumReplicas(3))
          .addCluster(new ClusterStatus()
              .withClusterName("cluster4").withReplicas(4).withMaximumReplicas(8).withMinimumReplicas(2)
              .withObservedGeneration(2L));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder(
                "REMOVE /status/clusters/1/readyReplicas",
                "REPLACE /status/clusters/1/replicas 2",
                "REMOVE /status/clusters/2",
                "ADD /status/clusters/- {'clusterName':'cluster4','maximumReplicas':8,"
                        + "'minimumReplicas':2,'observedGeneration':2,'replicas':4}"
                ));
  }

  @Test
  void excludingHealthWhenOnlyNewStatusHasServers_addThem() {
    DomainStatus status1 = new DomainStatus();
    DomainStatus status2 = new DomainStatus()
          .addServer(new ServerStatus()
                .withServerName("ms1").withClusterName("cluster1")
                .withState(RUNNING_STATE).withNodeName("node1").withStateGoal(RUNNING_STATE))
          .addServer(new ServerStatus()
                .withServerName("ms2").withClusterName("cluster1").withState(STARTING_STATE));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder(
             "ADD /status/servers/- {'clusterName':'cluster1','nodeName':'node1',"
                 + "'serverName':'ms1','state':'RUNNING','stateGoal':'RUNNING'}",
             "ADD /status/servers/- {'clusterName':'cluster1','serverName':'ms2','state':'STARTING'}"
             ));
  }

  @Test
  void excludingHealthWhenOnlyOldStatusHasServers_removeThem() {
    DomainStatus status1 = new DomainStatus()
          .addServer(new ServerStatus().withServerName("ms1").withClusterName("cluster1"))
          .addServer(new ServerStatus().withServerName("ms2").withClusterName("cluster1"));
    DomainStatus status2 = new DomainStatus();

    computePatch(status1, status2);

    assertThat(builder.getPatches(), hasItemsInOrder("REMOVE /status/servers/1", "REMOVE /status/servers/0"));
  }

  private OffsetDateTime now() {
    // Truncate to seconds because we intermittently see a different number of trailing decimals
    // that can cause the string comparison to fail
    return SystemClock.now().truncatedTo(ChronoUnit.SECONDS);
  }

  @Test
  void withHealthScalarsWhenOnlyNewStatusHasServers_addThem() {
    OffsetDateTime activationTime = now();
    DomainStatus status1 = new DomainStatus();
    DomainStatus status2 = new DomainStatus()
          .addServer(new ServerStatus()
                .withServerName("ms1").withClusterName("cluster1")
                .withHealth(new ServerHealth().withOverallHealth("AOK").withActivationTime(activationTime)))
          .addServer(new ServerStatus()
                .withServerName("ms2").withClusterName("cluster1").withState(STARTING_STATE));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder(
                "ADD /status/servers/- {'clusterName':'cluster1',"
                      + "'health':{'activationTime':'" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(activationTime)
                      + "','overallHealth':'AOK'},"
                      + "'serverName':'ms1'}",
                "ADD /status/servers/- {'clusterName':'cluster1','serverName':'ms2','state':'STARTING'}"
                ));
  }

  @Test
  void withHealthScalarsWhenBothStatusesHasServers_modifyThem() {
    OffsetDateTime activationTime = now();
    DomainStatus status1 = new DomainStatus()
          .addServer(new ServerStatus()
                .withServerName("ms1").withClusterName("cluster1")
                .withHealth(new ServerHealth().withOverallHealth("AOK")))
          .addServer(new ServerStatus()
                .withServerName("ms2").withClusterName("cluster1")
                .withHealth(new ServerHealth().withOverallHealth("starting").withActivationTime(activationTime)));
    DomainStatus status2 = new DomainStatus()
          .addServer(new ServerStatus()
                .withServerName("ms2").withClusterName("cluster1")
                .withHealth(new ServerHealth().withOverallHealth("AOK").withActivationTime(activationTime)))
          .addServer(new ServerStatus()
                .withServerName("ms3").withClusterName("cluster1").withState(STARTING_STATE));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder(
                "REPLACE /status/servers/1/health/overallHealth 'AOK'",
                "REMOVE /status/servers/0",
                "ADD /status/servers/- {'clusterName':'cluster1','serverName':'ms3','state':'STARTING'}"
                ));
  }

  @Test
  void withSubsystemHealthWhenOnlyNewStatusHasSubsystemValues_addThem() {
    OffsetDateTime activationTime = now();
    DomainStatus status1 = new DomainStatus()
          .addServer(new ServerStatus().withServerName("ms1"))
          .addServer(new ServerStatus().withServerName("ms2")
                .withHealth(new ServerHealth().withOverallHealth("OK")));
    DomainStatus status2 = new DomainStatus()
          .addServer(new ServerStatus().withServerName("ms1")
                .withHealth(new ServerHealth().withOverallHealth("Confused").withActivationTime(activationTime)
                .addSubsystem(new SubsystemHealth().withSubsystemName("ejb").withHealth("confused"))))
          .addServer(new ServerStatus().withServerName("ms2")
                .withHealth(new ServerHealth().withOverallHealth("Lagging")
                .addSubsystem(new SubsystemHealth().withSubsystemName("web").withHealth("slow"))))
          .addServer(new ServerStatus().withServerName("ms3")
                .withHealth(new ServerHealth().withOverallHealth("Broken").withActivationTime(activationTime)
                .addSubsystem(new SubsystemHealth().withSubsystemName("jmx").withHealth("obsolete"))
                .addSubsystem(new SubsystemHealth().withSubsystemName("sockets").withHealth("uninitialized"))));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder(
                "ADD /status/servers/0/health "
                      + "{'activationTime':'" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(activationTime)
                      + "','overallHealth':'Confused','subsystems':[{'health':'confused','subsystemName':'ejb'}]}",
                "REPLACE /status/servers/1/health/overallHealth 'Lagging'",
                "ADD /status/servers/1/health/subsystems/- {'health':'slow','subsystemName':'web'}",
                "ADD /status/servers/- "
                      + "{'health':"
                      +     "{'activationTime':'" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(activationTime)
                      + "','overallHealth':'Broken',"
                      +      "'subsystems':["
                      +         "{'health':'obsolete','subsystemName':'jmx'},"
                      +         "{'health':'uninitialized','subsystemName':'sockets'}"
                      +      "]},"
                      + "'serverName':'ms3'}"
                ));
  }

  @Test
  void whenSubsystemRemovedOrModified_patchAsNeeded() {
    OffsetDateTime activationTime = now();
    DomainStatus status1 = new DomainStatus()
          .addServer(new ServerStatus().withServerName("ms1")
                .withHealth(new ServerHealth().withOverallHealth("Confused").withActivationTime(activationTime)
                .addSubsystem(new SubsystemHealth().withSubsystemName("ejb").withHealth("confused"))))
          .addServer(new ServerStatus().withServerName("ms2")
                .withHealth(new ServerHealth().withOverallHealth("Lagging")
                .addSubsystem(new SubsystemHealth().withSubsystemName("web").withHealth("slow"))
                .addSubsystem(new SubsystemHealth().withSubsystemName("jmx").withHealth("obsolete"))))
          .addServer(new ServerStatus().withServerName("ms3")
                .withHealth(new ServerHealth().withOverallHealth("Broken").withActivationTime(activationTime)
                .addSubsystem(new SubsystemHealth().withSubsystemName("web").withHealth("slow"))
                .addSubsystem(new SubsystemHealth().withSubsystemName("sockets").withHealth("uninitialized"))));
    DomainStatus status2 = new DomainStatus()
          .addServer(new ServerStatus().withServerName("ms1")
                .withHealth(new ServerHealth().withOverallHealth("Confused").withActivationTime(activationTime)
                .addSubsystem(new SubsystemHealth().withSubsystemName("ejb").withHealth("improving"))))
          .addServer(new ServerStatus().withServerName("ms2")
                .withHealth(new ServerHealth().withOverallHealth("Lagging")
                .addSubsystem(new SubsystemHealth().withSubsystemName("web").withHealth("slow"))))
          .addServer(new ServerStatus().withServerName("ms3")
                .withHealth(new ServerHealth().withOverallHealth("Broken").withActivationTime(activationTime)));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder(
                "REPLACE /status/servers/0/health/subsystems/0/health 'improving'",
                "REMOVE /status/servers/1/health/subsystems/1",
                "REMOVE /status/servers/2/health/subsystems/1",
                "REMOVE /status/servers/2/health/subsystems/0"
                ));
  }

  @Test
  void whenSubsystemSymptomsAddedAndRemoved_addAndRemove() {
    DomainStatus status1 = new DomainStatus()
          .addServer(new ServerStatus().withServerName("ms1")
                .withHealth(new ServerHealth()
                .addSubsystem(new SubsystemHealth().withSubsystemName("ejb").withSymptoms("s2","s4"))));
    DomainStatus status2 = new DomainStatus()
          .addServer(new ServerStatus().withServerName("ms1")
                .withHealth(new ServerHealth()
                .addSubsystem(new SubsystemHealth().withSubsystemName("ejb").withSymptoms("s1", "s2", "s3"))));

    computePatch(status1, status2);

    assertThat(builder.getPatches(),
          hasItemsInOrder(
                "REMOVE /status/servers/0/health/subsystems/0/symptoms/1",
                "ADD /status/servers/0/health/subsystems/0/symptoms/- 's1'",
                "ADD /status/servers/0/health/subsystems/0/symptoms/- 's3'"
                ));
  }

  abstract static class PatchBuilderStub implements JsonPatchBuilder {
    private final List<String> patches = new ArrayList<>();

    String[] getPatches() {
      return patches.toArray(new String[0]);
    }

    @Override
    public JsonPatchBuilder add(String s, boolean b) {
      patches.add("ADD " + s + " " + b);
      return this;
    }

    @Override
    public JsonPatchBuilder add(String s, JsonValue jsonValue) {
      if (jsonValue == JsonValue.EMPTY_JSON_OBJECT) {
        patches.add("ADD " + s);
      } else {
        patches.add("ADD " + s + " " + toPatchString(jsonValue));
      }
      return this;     
    }

    @Override
    public JsonPatchBuilder add(String s, String s1) {
      patches.add("ADD " + s + " '" + s1 + "'");
      return this;
    }

    @Override
    public JsonPatchBuilder add(String s, int i) {
      patches.add("ADD " + s + " " + i);
      return this;
    }

    @Override
    public JsonPatchBuilder remove(String s) {
      patches.add("REMOVE " + s);
      return this;
    }

    @Override
    public JsonPatchBuilder replace(String s, int i) {
      patches.add("REPLACE " + s + " " + i);
      return this;
    }

    @Override
    public JsonPatchBuilder replace(String s, String s1) {
      patches.add("REPLACE " + s + " '" + s1 + "'");
      return this;
    }

    private String toPatchString(JsonValue jsonValue) {
      if (jsonValue.equals(JsonObject.FALSE)) {
        return "'false'";
      } else if (jsonValue.equals(JsonValue.TRUE)) {
        return "'true'";
      } else if (jsonValue instanceof JsonString) {
        return "'" + ((JsonString) jsonValue).getString() + "'";
      } else if (jsonValue instanceof JsonNumber) {
        return ((JsonNumber) jsonValue).numberValue().toString();
      } else if (jsonValue instanceof JsonObject) {
        return "{" + toPatchFieldStream(jsonValue.asJsonObject()).collect(Collectors.joining(",")) + "}";
      } else if (jsonValue instanceof JsonArray) {
        return "[" + toPatchFieldStream(jsonValue.asJsonArray()).collect(Collectors.joining(",")) + "]";
      } else {
        return "";
      }
    }

    private Stream<String> toPatchFieldStream(JsonObject jsonObject) {
      return jsonObject.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(this::toPatchField);
    }

    private Stream<String> toPatchFieldStream(JsonArray jsonArray) {
      return jsonArray.stream().map(this::toPatchString);
    }

    private String toPatchField(Map.Entry<String,JsonValue> entry) {
      return "'" + entry.getKey() + "':" + toPatchString(entry.getValue());
    }
  }


  @SuppressWarnings("unused")
  static class OrderedArrayMatcher extends TypeSafeDiagnosingMatcher<String[]> {
    private final String[] expectedItems;

    private OrderedArrayMatcher(String[] expectedItems) {
      this.expectedItems = expectedItems;
    }

    static OrderedArrayMatcher hasItemsInOrder(String... items) {
      return new OrderedArrayMatcher(items);
    }

    @Override
    protected boolean matchesSafely(String[] array, Description mismatchDescription) {
      int j = 0;
      for (String expectedItem : expectedItems) {
        j = foundIndex(array, expectedItem, j);
        if (j++ < 0) {
          return itemNotFound(expectedItem, array, mismatchDescription);
        }
      }
      return true;
    }

    private int foundIndex(String[] array, String expectedItem, int startIndex) {
      for (int i = startIndex; i < array.length; i++) {
        if (Objects.equals(expectedItem, array[i])) {
          return i;
        }
      }

      return -1;
    }

    private boolean itemNotFound(String expectedItem, String[] array, Description mismatchDescription) {
      mismatchDescription
            .appendText("did not find ").appendValue(expectedItem)
            .appendText(" in order in ").appendValueList("[", ",", "]", array);
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("expected array containing, in order, ").appendValueList("[", ",", "]", expectedItems);
    }
  }
}