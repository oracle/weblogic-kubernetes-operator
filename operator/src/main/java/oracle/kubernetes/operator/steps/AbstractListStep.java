// Copyright (c) 2017, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collection;
import java.util.Iterator;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.ProcessingConstants;
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
  public @Nonnull Result apply(Packet packet) {
    if (it.hasNext()) {
      CoreDelegate delegate = (CoreDelegate) packet.get(ProcessingConstants.DELEGATE_COMPONENT_NAME);
      return doNext(createActionStep(delegate, it.next()), packet);
    } else {
      return doNext(packet);
    }
  }

  abstract Step createActionStep(CoreDelegate delegate, T item);
}
