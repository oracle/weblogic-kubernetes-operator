// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collection;
import java.util.Iterator;

import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * A step which will perform an action on each entry in the specified collection. It does so by chaining back to
 * itself in the response step, in order to process the next entry in the iterator.
 */
public abstract class AbstractListStep<T> extends Step {
  private final Iterator<T> it;

  AbstractListStep(Collection<T> c, Step next) {
    super(next);
    this.it = c.iterator();
  }

  @Override
  public NextAction apply(Packet packet) {
    if (it.hasNext()) {
      return doNext(createActionStep(it.next()), packet);
    } else {
      return doNext(packet);
    }
  }

  abstract Step createActionStep(T item);
}
