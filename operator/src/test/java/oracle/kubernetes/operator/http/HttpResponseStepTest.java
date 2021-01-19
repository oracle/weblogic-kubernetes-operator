// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;

import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.typeCompatibleWith;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class HttpResponseStepTest {

  private final TerminalStep terminalStep = new TerminalStep();
  private final HttpResponseStepImpl responseStep = new HttpResponseStepImpl(terminalStep);

  @Test
  public void classImplementsStep() {
    assertThat(HttpResponseStep.class, typeCompatibleWith(Step.class));
  }

  @Test
  public void classIsAbstract() {
    assertThat(Modifier.isAbstract(HttpResponseStep.class.getModifiers()), is(true));
  }

  @Test
  public void constructorSetsNextStep() {
    assertThat(responseStep.getNext(), sameInstance(terminalStep));
  }

  @Test
  public void classHasOnSuccessMethod() throws NoSuchMethodException {
    assertThat(
          HttpResponseStep.class.getDeclaredMethod("onSuccess", Packet.class, HttpResponse.class),
          notNullValue());
  }

  @Test
  public void classHasOnFailureMethod() throws NoSuchMethodException {
    assertThat(
          HttpResponseStep.class.getDeclaredMethod("onFailure", Packet.class, HttpResponse.class),
          notNullValue());
  }

  @Test
  public void whenResponseIsSuccess_invokeOnSuccess() {
    Packet packet = new Packet();
    HttpResponseStep.addToPacket(packet, createStub(HttpResponseStub.class, HttpURLConnection.HTTP_OK));

    responseStep.apply(packet);

    assertThat(responseStep.getSuccessResponse(), notNullValue());
    assertThat(responseStep.getFailureResponse(), nullValue());
  }

  @Test
  public void whenResponseIsFailure_invokeOnFailure() {
    Packet packet = new Packet();
    HttpResponseStep.addToPacket(packet, createStub(HttpResponseStub.class, HttpURLConnection.HTTP_FORBIDDEN));

    responseStep.apply(packet);

    assertThat(responseStep.getSuccessResponse(), nullValue());
    assertThat(responseStep.getFailureResponse(), notNullValue());
  }

  @Test
  public void whenNoResponseProvided_skipProcessing() {
    NextAction nextAction = responseStep.apply(new Packet());

    assertThat(responseStep.getSuccessResponse(), nullValue());
    assertThat(responseStep.getFailureResponse(), nullValue());
    assertThat(nextAction.getNext(), sameInstance(terminalStep));
  }

  // todo when response is failure, invoke onFailure
}
