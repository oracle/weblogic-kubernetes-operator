// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class LastKnownStatus {
  private final String status;
  private final int unchangedCount;
  private final OffsetDateTime time;

  public LastKnownStatus(String status) {
    this(status, 0);
  }

  /**
   * Construct last known status.
   * @param status status
   * @param unchangedCount unchanged count
   */
  public LastKnownStatus(String status, int unchangedCount) {
    this.status = status;
    this.unchangedCount = unchangedCount;
    this.time = OffsetDateTime.now();
  }

  public String getStatus() {
    return status;
  }

  public int getUnchangedCount() {
    return unchangedCount;
  }

  public OffsetDateTime getTime() {
    return time;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("status", status)
        .append("unchangedCount", unchangedCount)
        .append("time", time)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LastKnownStatus that = (LastKnownStatus) o;

    // ignore time
    return new EqualsBuilder()
        .append(status, that.status)
        .append(unchangedCount, that.unchangedCount)
        .isEquals();
  }

  @Override
  public int hashCode() {
    // ignore time
    return new HashCodeBuilder().append(status).append(unchangedCount).toHashCode();
  }
}
