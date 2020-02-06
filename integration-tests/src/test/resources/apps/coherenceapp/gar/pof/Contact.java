// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package com.examples.pof;

import java.io.IOException;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;
import com.tangosol.util.Base;

/**
 * POF serialization implementation for coherence cache.
 */
public class Contact implements PortableObject {

  // The POF index for the FirstName property.
  public static final int FIRSTNAME = 0;

  // The POF index for the LastName property.
  public static final int LASTNAME = 1;

  private String msFirstName;
  private String msLastName;

  public Contact() {
  }

  public Contact(String firstName, String lastName) {
    msFirstName = firstName;
    msLastName = lastName;
  }

  /**
   * Return the first name. 
   */
  public String getFirstName() {
    return msFirstName;
  }

  /**
   * Set the first name.
   * @param firstName the first name
   */
  public void setFirstName(String firstName) {
    msFirstName = firstName;
  }

  /**
   * Return the last name.
   */
  public String getLastName() {
    return msLastName;
  }

  /**
   * Set the last name.
   * @param lastName the last name
   */
  public void setLastName(String lastName) {
    msLastName = lastName;
  }

  public void readExternal(PofReader reader) throws IOException {
    msFirstName = reader.readString(FIRSTNAME);
    msLastName = reader.readString(LASTNAME);
  }

  public void writeExternal(PofWriter writer) throws IOException {
    writer.writeString(FIRSTNAME, msFirstName);
    writer.writeString(LASTNAME, msLastName);
  }

  public String toString() {
    StringBuilder sb = new StringBuilder(getFirstName()).append(" ").append(getLastName());
    return sb.toString();
  }

  /**
   * Returns the hashCode of the Contact.
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;

    result = prime * result + ((msFirstName == null) ? 0 : msFirstName.hashCode());
    result = prime * result + ((msLastName == null) ? 0 : msLastName.hashCode());
    return result;
  }

  /**
   * Compares this Contact with another.
   * @param obj object
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    Contact that = (Contact) obj;
    return Base.equals(that.getFirstName(), getFirstName()) && Base.equals(that.getLastName(), getLastName());
  }
}
