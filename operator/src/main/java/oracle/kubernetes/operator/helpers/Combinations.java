// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Combinations {
  private Combinations() {
    // no-op
  }

  /**
   * Generates a stream of lists containing all combinations of elements from input data.
   * @param <T> Data type
   * @param data List of elements
   * @return Stream of lists containing all combinations of elements from input data
   */
  public static <T> Stream<List<T>> of(List<T> data) {
    final long N = (long) Math.pow(2, data.size());
    return StreamSupport.stream(new Spliterators.AbstractSpliterator<>(N, Spliterator.SIZED) {
      long index = 1;

      @Override
      public boolean tryAdvance(Consumer<? super List<T>> action) {
        if (index < N) {
          List<T> out = new ArrayList<>(Long.bitCount(index));
          for (int bit = 0; bit < data.size(); bit++) {
            if ((index & (1 << bit)) != 0) {
              out.add(data.get(bit));
            }
          }
          action.accept(out);
          ++index;
          return true;
        } else {
          return false;
        }
      }
    }, false);
  }
}
