// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.atomic.AtomicReference;

/**
 * This class determines an instance of {@link Container} for the runtime.
 *
 * <p>ContainerResolver uses a static field to keep the instance of the resolver object. Typically,
 * a server may set its custom container resolver using the static method {@link
 * #setInstance(ContainerResolver)}
 */
public abstract class ContainerResolver {

  private static final ThreadLocalContainerResolver DEFAULT = new ThreadLocalContainerResolver();

  private static final AtomicReference<ContainerResolver> theResolver =
      new AtomicReference(DEFAULT);

  /**
   * Sets the custom container resolver which can be used to get client's {@link Container}.
   *
   * @param resolver container resolver
   */
  public static void setInstance(ContainerResolver resolver) {
    if (resolver == null) {
      resolver = DEFAULT;
    }
    theResolver.set(resolver);
  }

  /**
   * Returns the container resolver which can be used to get client's {@link Container}.
   *
   * @return container resolver instance
   */
  public static ContainerResolver getInstance() {
    return theResolver.get();
  }

  /**
   * Returns the default container resolver which can be used to get {@link Container}.
   *
   * @return default container resolver
   */
  public static ThreadLocalContainerResolver getDefault() {
    return DEFAULT;
  }

  /**
   * Returns the {@link Container} context in which client is running.
   *
   * @return container instance for the client
   */
  public abstract Container getContainer();
}
