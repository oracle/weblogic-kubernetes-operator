// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package com.examples.pof;

import java.io.IOException;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;
import com.tangosol.util.Base;

/**
 * ContactId represents a key to the contact for whom information is stored in
 * the cache.
 * The type implements PortableObject for efficient cross-platform 
 */
public class ContactId implements PortableObject {

  //The POF index for the FirstName property.
  public static final int FIRSTNAME = 0;

  //The POF index for the LastName property.
  public static final int LASTNAME = 1;

  private String msFirstName;
  private String msLastName;

  /**
   * Default constructor (necessary for PortableObject implementation).
   */
  public ContactId() {
  }

  /**
   * Construct a contact key.
   * @param firstName first name
   * @param lastName last name
   */
  public ContactId(String firstName, String lastName) {
    msFirstName = firstName;
    msLastName = lastName;
  }

  public String getFirstName() {
    return msFirstName;
  }

  public String getLastName() {
    return msLastName;
  }

  public void readExternal(PofReader reader) throws IOException {
    msFirstName = reader.readString(FIRSTNAME);
    msLastName = reader.readString(LASTNAME);
  }

  public void writeExternal(PofWriter writer) throws IOException {
    writer.writeString(FIRSTNAME, msFirstName);
    writer.writeString(LASTNAME, msLastName);
  }

  /**
   * check equality.
   * @param otherThat other
   * @return true, if equal
   */
  public boolean equals(Object otherThat) {
    if (this == otherThat) {
      return true;
    }

    if (otherThat == null) {
      return false;
    }

    ContactId that = (ContactId) otherThat;

    return Base.equals(getFirstName(), that.getFirstName()) && Base.equals(getLastName(), that.getLastName());
  }

  public int hashCode() {
    return (getFirstName() == null ? 0 : getFirstName().hashCode())
                ^ (getLastName() == null ? 0 : getLastName().hashCode());
  }

  public String toString() {
    return getFirstName() + " " + getLastName();
  }
}
