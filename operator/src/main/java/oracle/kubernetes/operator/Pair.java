// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Pair<L, R> {
  private final L left;
  private final R right;

  public Pair(L left, R right) {
    this.left = left;
    this.right = right;
  }

  public L getLeft() {
    return left;
  }

  public R getRight() {
    return right;
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
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Pair)) {
      return false;
    }
    Pair rhs = ((Pair) other);
    return new EqualsBuilder().append(left, rhs.left).append(right, rhs.right).isEquals();
  }
}
