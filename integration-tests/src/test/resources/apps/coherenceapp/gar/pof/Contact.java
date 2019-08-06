/**
 *  * 
 *   */
package com.examples.pof;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;

import com.tangosol.util.Base;

import java.io.IOException;

import java.util.Date;

/**
 * * @author Sanjay Mantoor *
 *  POF serialization implementation for coherence cache
 */
public class Contact implements PortableObject {

	private String m_sFirstName;
	private String m_sLastName;

	public Contact() {

	}

	public Contact(String firstName, String lastName) {
		m_sFirstName = firstName;
		m_sLastName = lastName;
	}

	/**
	 * * Return the first name. * * @return the first name
	 */
	public String getFirstName() {
		return m_sFirstName;
	}

	/**
	 * * Set the first name. * * @param sFirstName the first name
	 */
	public void setFirstName(String sFirstName) {
		m_sFirstName = sFirstName;
	}

	/**
	 * * Return the last name. * * @return the last name
	 */
	public String getLastName() {
		return m_sLastName;
	}

	/**
	 * * Set the last name. * * @param sLastName the last name
	 */
	public void setLastName(String sLastName) {
		m_sLastName = sLastName;
	}

	/**
	 * * {@inheritDoc}
	 */
	public void readExternal(PofReader reader) throws IOException {
		m_sFirstName = reader.readString(FIRSTNAME);
		m_sLastName = reader.readString(LASTNAME);
	}

	/**
	 * * {@inheritDoc}
	 */
	public void writeExternal(PofWriter writer) throws IOException {
		writer.writeString(FIRSTNAME, m_sFirstName);
		writer.writeString(LASTNAME, m_sLastName);
	}

	/**
	 * * {@inheritDoc}
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder(getFirstName()).append(" ").append(getLastName());
		return sb.toString();
	}

	/**
	 * * Returns the hashCode of this {@link Contact}. * * @return the hashCode
	 * of this {@link Contact}
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((m_sFirstName == null) ? 0 : m_sFirstName.hashCode());
		result = prime * result + ((m_sLastName == null) ? 0 : m_sLastName.hashCode());
		return result;
	}

	/**
	 * * Compares this {@link Contact} with another. * * @param obj
	 * {@link Contact} to compare to * * @return if the contacts are equal
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

	/**
	 * * The POF index for the FirstName property.
	 */
	public static final int FIRSTNAME = 0;

	/**
	 * * The POF index for the LastName property.
	 */
	public static final int LASTNAME = 1;

}
