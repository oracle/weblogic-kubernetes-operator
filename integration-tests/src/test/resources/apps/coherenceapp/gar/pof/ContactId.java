// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package com.examples.pof;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;

import com.tangosol.util.Base;

import java.io.IOException;

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

  private String m_sFirstName;
  private String m_sLastName;

  /**
   * Default constructor (necessary for PortableObject implementation).
   */
  public ContactId() {
  }

  /**
   * Construct a contact key
   * @param sFirstName first name
   * @param sLastName last name
   */
  public ContactId(String sFirstName, String sLastName) {
    m_sFirstName = sFirstName;
    m_sLastName = sLastName;
  }

  public String getFirstName() {
    return m_sFirstName;
  }

  public String getLastName() {
    return m_sLastName;
  }

  public void readExternal(PofReader reader) throws IOException {
    m_sFirstName = reader.readString(FIRSTNAME);
    m_sLastName = reader.readString(LASTNAME);
  }

  public void writeExternal(PofWriter writer) throws IOException {
    writer.writeString(FIRSTNAME, m_sFirstName);
    writer.writeString(LASTNAME, m_sLastName);
  }

  public boolean equals(Object oThat) {
    if (this == oThat) {
      return true;
    }

    if (oThat == null) {
      return false;
    }

    ContactId that = (ContactId) oThat;

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
