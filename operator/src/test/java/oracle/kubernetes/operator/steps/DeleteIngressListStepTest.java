package oracle.kubernetes.operator.steps;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1Ingress;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.work.AsyncCallTestSupport;
import oracle.kubernetes.operator.work.TerminalStep;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DeleteIngressListStepTest {

  private Collection<V1beta1Ingress> ingresses = new ArrayList<>();
  private TerminalStep terminalStep = new TerminalStep();

  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(TestUtils.silenceOperatorLogger());
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();
    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenCollectionEmpty_makeNoCalls() throws Exception {
    runDeleteStep();
  }

  private void runDeleteStep(V1beta1Ingress... ingresses) {
    this.ingresses.addAll(Arrays.asList(ingresses));
    testSupport.runSteps(new DeleteIngressListStep(this.ingresses, terminalStep));
  }

  @Test
  public void whenCollectionContainsItems_invokeDeleteCalls() throws Exception {
    defineResponse("namespace1", "name1").returning(new V1Status());
    defineResponse("namespace2", "name2").returning(new V1Status());

    runDeleteStep(
        new V1beta1Ingress().metadata(new V1ObjectMeta().namespace("namespace1").name("name1")),
        new V1beta1Ingress().metadata(new V1ObjectMeta().namespace("namespace2").name("name2")));

    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @SuppressWarnings("unchecked")
  private <T> AsyncCallTestSupport.CannedResponse<T> defineResponse(String namespace, String name) {
    return testSupport
        .createCannedResponse("deleteIngress")
        .withNamespace(namespace)
        .withName(name);
  }

  @Test
  public void onFailureResponse_reportError() throws Exception {
    defineResponse("namespace1", "name1").failingWithStatus(HttpURLConnection.HTTP_FORBIDDEN);

    runDeleteStep(
        new V1beta1Ingress().metadata(new V1ObjectMeta().namespace("namespace1").name("name1")));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void onNotFoundResponse_dontReportError() throws Exception {
    defineResponse("namespace1", "name1").failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);

    runDeleteStep(
        new V1beta1Ingress().metadata(new V1ObjectMeta().namespace("namespace1").name("name1")));
  }
}
