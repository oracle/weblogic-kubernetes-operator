// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.atomic.AtomicReference;

/**
 * This class determines an instance of {@link Container} for the runtime.
 *
 * <p>ContainerResolver uses a static field to keep the instance of the resolver object.
 */
public abstract class ContainerResolver {

  private static final ThreadLocalContainerResolver DEFAULT = new ThreadLocalContainerResolver();

  private static final AtomicReference<ContainerResolver> theResolver =
      new AtomicReference<>(DEFAULT);

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
