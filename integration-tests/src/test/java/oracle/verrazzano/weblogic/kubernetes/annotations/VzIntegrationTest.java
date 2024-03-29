// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic.kubernetes.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import oracle.verrazzano.weblogic.kubernetes.extensions.VzInitializationTasks;
import oracle.weblogic.kubernetes.extensions.IntegrationTestWatcher;
import oracle.weblogic.kubernetes.extensions.LoggingExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@ExtendWith(LoggingExtension.class)
@ExtendWith(VzInitializationTasks.class)
@ExtendWith(IntegrationTestWatcher.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public @interface VzIntegrationTest {
}
