// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

/**
 * Extended version of {@link Component}. Allows component to return multiple SPI implementations
 * through an {@link Iterable}.
 */
public interface ComponentEx extends Component {
  /**
   * Gets an iterator of implementations of the specified SPI.
   *
   * <p>This method works as a kind of directory service for SPIs, allowing various components to
   * define private contract and talk to each other. However unlike {@link
   * Component#getSpi(java.lang.Class)}, this method can support cases where there is an ordered
   * collection (defined by {@link Iterable} of implementations. The SPI contract should define
   * whether lookups are for the first appropriate implementation or whether all returned
   * implementations should be used.
   *
   * @param <S> SPI type
   * @param spiType SPI class
   * @return non-null {@link Iterable} of the SPI's provided by this object. Iterator may have no
   *     values.
   */
  <S> Iterable<S> getIterableSpi(Class<S> spiType);
}
