// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import oracle.weblogic.kubernetes.extensions.ImageBuilders;
import oracle.weblogic.kubernetes.extensions.IntegrationTestWatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@ExtendWith(ImageBuilders.class)
@ExtendWith(IntegrationTestWatcher.class)
public @interface IntegrationTest {
}
