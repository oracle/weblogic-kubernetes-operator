// Copyright (c) 2019, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public record Pair<L, R>(L left, R right) {

  /**
   * Create pair.
   * @param <A> Left type
   * @param <B> Right type
   * @param left left
   * @param right right
   * @return pair
   */
  public static <A, B> Pair<A, B> of(A left, B right) {
    return new Pair<A, B>(left, right);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).append("left", left).append("right", right).toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(left).append(right).toHashCode();
  }

  @Override
  @SuppressWarnings("rawtypes")
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Pair rhs)) {
      return false;
    }
    return new EqualsBuilder().append(left, rhs.left).append(right, rhs.right).isEquals();
  }
}
