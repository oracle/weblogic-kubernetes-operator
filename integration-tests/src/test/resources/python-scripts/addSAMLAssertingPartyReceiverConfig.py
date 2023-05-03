# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

admin_username=sys.argv[1]
admin_password=sys.argv[2]
admin_t3_url=sys.argv[3]
audience_uri=sys.argv[4]
script_name = 'addSAMLConfig.py'
print 'script_name: ' + script_name

print 'admin_t3_url: ' + admin_t3_url
print 'admin_username: ' + admin_username
print 'admin_password: ' + admin_password
print 'audience_uri: ' + audience_uri


def setup_saml2IdentityAsserter():
  try:
    print 'connecting to admin server'
    connect(admin_username, admin_password, admin_t3_url)
    rlm = cmo.getSecurityConfiguration().getDefaultRealm()

    # Setup SAML IA
    saml2ia = rlm.lookupAuthenticationProvider("Saml2IdentityAsserter")


    print 'setup_saml2IdentityAsserter()'
    edit()
    startEdit()

    targetLiteral = "target:-:"
    idp = saml2ia.newWSSIdPPartner()
    idp.setName(audience_uri)
    idp.setConfirmationMethod(idp.ASSERTION_TYPE_SENDER_VOUCHES)
    idp.setIssuerURI("http://foo.bar.com/saml2-issuer")
    idp.setAudienceURIs(array([ targetLiteral + audience_uri ], java.lang.String))
    idp.setEnabled(true)
    saml2ia.addIdPPartner(idp)

    save()
    activate(block='true')

    print 'done with setup_saml2IdentityAsserter'
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

setup_saml2IdentityAsserter()
exit()
