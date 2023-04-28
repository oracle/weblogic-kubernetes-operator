# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

admin_username=sys.argv[1]
admin_password=sys.argv[2]
admin_t3_url=sys.argv[3]
cp_partner_url=sys.argv[4]
script_name = 'addSAMLRelyingPartySenderConfig.py'
print 'script_name: ' + script_name

print 'admin_t3_url: ' + admin_t3_url
print 'admin_username: ' + admin_username
print 'admin_password: ' + admin_password
print 'cp_partner_url: ' + cp_partner_url


def setup_saml2_relying_party_sender():
  try:
    print 'connecting to admin server'
    connect(admin_username, admin_password, admin_t3_url)
    rlm = cmo.getSecurityConfiguration().getDefaultRealm()

    saml2cm = rlm.lookupCredentialMapper('Saml2CredentialMapper')

    print 'setup_saml2_relying_party_sender()'

    targetLiteral = "target:-:"
    # CONFIGURE NEW SP PARTNERS
    sp = saml2cm.newWSSSPPartner()
    sp.setName(cp_partner_url)
    sp.setConfirmationMethod(sp.ASSERTION_TYPE_SENDER_VOUCHES)
    sp.setAudienceURIs(array([ targetLiteral + cp_partner_url ], java.lang.String))
    sp.setEnabled(true)
    sp.setWantAssertionsSigned(false)

    saml2cm.addSPPartner(sp)

    print 'done with setup_saml2_relying_party_sender'
    disconnect()
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    exit(exitcode=1)
  except:
    print 'set failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

setup_saml2_relying_party_sender()
exit()
