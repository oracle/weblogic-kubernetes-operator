// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.http;

import org.junit.Test;
import oracle.kubernetes.operator.work.Step;

public class HttpResponseStepTest {
	@Test
	public void classImplementsStep() {
		
		assertThat(HttpResponseStep.class, typeCompatibleWith(Step.class)); 
		
	}
}
