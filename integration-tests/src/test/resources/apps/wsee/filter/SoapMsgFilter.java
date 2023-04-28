// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package saml.sendervouches.filter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.ResourceBundle;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import weblogic.xml.saaj.SOAPConstants;

public class SoapMsgFilter implements Filter {
  private static String SAMLXMLNS = "saml2";
  private FilterConfig filterConfig;
  private static int count = 0;
  private static String bodyBegin = null;
  private static String bodyEnd = null;
  private static String elementClose = ">";
  private String responseData = null;
  private String samlAssertionReplace = null;
  private String requestData = null;

  static {
    try {
      ResourceBundle resource = ResourceBundle.getBundle("RawXML");
      if (resource != null) {
        bodyBegin = (String) resource.getObject("bodyBegin");
        bodyEnd = (String) resource.getObject("bodyEnd");
      }
    } catch (Exception ne) {
      ne.printStackTrace();
    }
  }

  /** Filter soap request and response from web service.
   * @param req servlet request
   * @param res servlet response
   * @param chain filter chain
   * @throws IOException exception
   * @throws ServletException exception
   */
  public void doFilter(ServletRequest req,
                       ServletResponse res,
                       FilterChain chain) throws IOException, ServletException {

    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) res;

    try {

      trace("******* senderVouches DoFilter start ***** Number " + count++);

      printRequestInfo((HttpServletRequest) req);
      String soapAction = ((HttpServletRequest) req).getHeader("SOAPAction");

      RawXMLRequestWrapper rawReqWrapper = new RawXMLRequestWrapper(request);
      RawXMLResponseWrapper rawResWrapper = new RawXMLResponseWrapper(response);

      chain.doFilter(rawReqWrapper, rawResWrapper);


      // Get the request data
      requestData = rawReqWrapper.toString();

      if (requestData != null && requestData.length() > 0) {
        trace("***** ORIGINAL REQUEST *****:\n" + requestData);

        // Find the Assertion values in the request
        if (requestData.indexOf("Fault") == -1) {

          if (requestData.indexOf("SAMLV2.0") != (-1) && requestData.indexOf("Token11") != (-1)) {
            handleRequestSamlAssertionV20("2.0", "1.0");
          }
        }
      }

      // Get the response data
      if (rawReqWrapper.isRawXML()) {
        responseData = getReturnData(rawResWrapper.toString());
      } else {
        responseData = rawResWrapper.toString();
      }

      if (requestData != null && requestData.length() > 0) {
        trace("***** ORIGINAL RESPONSE *****:\n" + responseData);

        if (responseData.indexOf("Fault") != -1) {
          handleExpectedFaultInResponse();
        }
      }
    } catch (IOException io) {
      trace("IOException raised in senderVouches:SoapMsgFilter");
      io.printStackTrace();
      responseData = createSoapFaultStr(io);
    } catch (ServletException se) {
      trace("ServletException raised in senderVouches:SoapMsgFilter");
      se.printStackTrace();
      responseData = createSoapFaultStr(se);
    } catch (Exception e) {
      trace("Unexpected exception in senderVouches:SoapMsgFilter");
      e.printStackTrace();
      responseData = createSoapFaultStr(e);
    } finally {
      // Always send a response back to the client
      if (requestData != null && requestData.length() > 0) {
        trace("***** SENDING RESPONSE *****:\n" + responseData + "\n");
      }
      int contentLength = 0;
      if (responseData != null) {
        contentLength = responseData.length();
      }
      res.setContentLength(responseData.length());
      ServletOutputStream sos = res.getOutputStream();
      sos.println(responseData);
      sos.flush();
      trace("******* senderVouches DoFilter end *****\n\n");
    }
  }

  public FilterConfig getFilterConfig() {
    return this.filterConfig;
  }

  public void setFilterConfig(FilterConfig filterConfig) {
    this.filterConfig = filterConfig;
  }

  public void init(FilterConfig filterConfig) throws ServletException {
    this.filterConfig = filterConfig;
  }

  public void destroy() {
    this.filterConfig = null;
  }

  private boolean isRawXML(ServletRequest request) {
    try {
      ServletInputStream input = request.getInputStream();
      StringBuffer sbuf = new StringBuffer();
      int i = input.read();
      while (i != -1) {
        sbuf.append((char) i);
        i = input.read();
      }
      if (sbuf.length() > 0) {
        String inputStr = sbuf.toString();
        trace("InputStream read:\n" + inputStr);

        String soapEnvelope = SOAPConstants.ENV_PREFIX + ":Envelope";
        if (inputStr.indexOf(soapEnvelope) == -1) {
          return true;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return false;
  }

  private void printRequestInfo(HttpServletRequest request) {
    trace("Request Data:");
    trace("URL is: " + request.getRequestURL());
    trace("URI is: " + request.getRequestURI());
    trace("ContextPath is: " + request.getContextPath());
    trace("Method is: " + request.getMethod());
    trace("Protocol is: " + request.getProtocol());
    trace("Scheme is: " + request.getScheme());
    trace("Query String is: " + request.getQueryString());

    Enumeration enum1 = request.getAttributeNames();
    String name = null;
    while (enum1.hasMoreElements()) {
      name = (String) enum1.nextElement();
      trace("Attribute: " + name + " Value: " + request.getAttribute(name).toString());
    }

    enum1 = request.getParameterNames();
    name = null;
    while (enum1.hasMoreElements()) {
      name = (String) enum1.nextElement();
      trace("Parameter: " + name + " Value: " + request.getParameter(name).toString());
    }

    enum1 = request.getHeaderNames();
    name = null;
    while (enum1.hasMoreElements()) {
      name = (String) enum1.nextElement();
      trace("Header: " + name + " Value: " + request.getHeader(name).toString());
    }

  }

  private String getReturnData(String data) {
    String returnData = null;

    int startIndex = -1;
    int endIndex = -1;

    startIndex = data.indexOf(bodyBegin);
    if (startIndex == -1) {
      trace("Can't find: " + bodyBegin);
      return data;
    }

    startIndex = data.indexOf(elementClose, startIndex);
    if (startIndex == -1) {
      trace("Can't find: " + elementClose);
      return data;
    }

    endIndex = data.indexOf(bodyEnd, startIndex);
    if (endIndex == -1) {
      trace("Can't find: " + bodyEnd);
      return data;
    }

    returnData = data.substring(startIndex + 1, endIndex);
    trace("Return:\n " + returnData);

    return returnData;
  }

  private void trace(String data) {
    System.out.println("senderVouches:SoapMsgFilter: " + data);
  }

  private void handleRequestSamlAssertionV11(String samlVersion, String samlTokenVersion) throws Exception {
    int numSigValues = 0;
    int start = 0;
    int end = 0;


    String namesp = "urn:oasis:names:tc:SAML:" + samlVersion + ":assertion";
    String subjectconf = "urn:oasis:names:tc:SAML:" + samlVersion + ":cm:sender-vouches";
    start = 0;
    start = requestData.indexOf("<Assertion", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for Assertion ");
    }
    end = requestData.indexOf("</Assertion>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for Assertion");
    }
    start = requestData.indexOf(namesp, start);
    if (start == -1) {
      throw new Exception("Did not find expected namespace for saml, expected " + namesp);
    }
    start = requestData.indexOf("<Conditions", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for Conditions ");
    }
    start = requestData.indexOf("<AuthenticationStatement", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for AuthenticationStatement ");
    }
    end = requestData.indexOf("</AuthenticationStatement>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for AuthenticationStatement");
    }
    start = requestData.indexOf("<Subject ", start) > 0
        || requestData.indexOf("<Subject>", start) > 0 ? 1 : -1;
    if (start == -1) {
      throw new Exception("Did not find start tag for Subject ");
    }
    end = requestData.indexOf("</Subject>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for Subject");
    }
    start = requestData.indexOf("<NameIdentifier", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for NameIdentifier ");
    }
    end = requestData.indexOf("</NameIdentifier>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for NameIdentifier");
    }
    start = requestData.indexOf("<SubjectConfirmation ", start) > 0
        || requestData.indexOf("<SubjectConfirmation>", start) > 0 ? 1 : -1;
    if (start == -1) {
      throw new Exception("Did not find start tag for SubjectConfirmation ");
    }
    end = requestData.indexOf("</SubjectConfirmation>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for SubjectConfirmation");
    }
    int mystart = requestData.indexOf(subjectconf, start);
    if (mystart == -1) {
      throw new Exception("Did not find expected subjectconfirmation for saml, expected " + subjectconf);
    }
    start = requestData.indexOf("<ConfirmationMethod ", start) > 0
        || requestData.indexOf("<ConfirmationMethod>", start) > 0 ? 1 : -1;
    if (start == -1) {
      throw new Exception("Did not find start tag for ConfirmationMethod ");
    }
    end = requestData.indexOf("</ConfirmationMethod>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for ConfirmationMethod");
    }
  }

  private void handleRequestSamlNegative(String original, String replaced) throws Exception {
    requestData = requestData.replace(original, replaced);

  }

  private void handleRequestKeepSamlAssertion() throws Exception {
    int start = 0;
    int end = 0;


    start = requestData.indexOf("<" + SAMLXMLNS + ":Assertion", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for Assertion ");
    }
    end = requestData.indexOf("</" + SAMLXMLNS + ":Assertion>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for Assertion");
    }
    samlAssertionReplace = requestData.substring(start, end + ("</" + SAMLXMLNS + ":Assertion>").length());
    trace(samlAssertionReplace);

  }

  private void handleRequestSamlAssertionV20(String samlVersion, String samlTokenVersion) throws Exception {
    int numSigValues = 0;
    int start = 0;
    int end = 0;


    String namesp = "urn:oasis:names:tc:SAML:" + samlVersion + ":assertion";
    String subjectconf = "urn:oasis:names:tc:SAML:" + samlVersion + ":cm:sender-vouches";
    start = 0;
    start = requestData.indexOf("<" + SAMLXMLNS + ":Assertion", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for Assertion ");
    }
    end = requestData.indexOf("</" + SAMLXMLNS + ":Assertion>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for Assertion");
    }
    start = requestData.indexOf(namesp, start);
    if (start == -1) {
      throw new Exception("Did not find expected namespace for saml, expected " + namesp);
    }
    start = requestData.indexOf("<" + SAMLXMLNS + ":Subject>", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for " + "<" + SAMLXMLNS + ":Subject> ");
    }
    end = requestData.indexOf("</" + SAMLXMLNS + ":Subject>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for " + "<" + SAMLXMLNS + ":Subject>");
    }
    start = requestData.indexOf("<" + SAMLXMLNS + ":NameID", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for " + "<" + SAMLXMLNS + ":NameID ");
    }
    end = requestData.indexOf("</" + SAMLXMLNS + ":NameID>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for " + "<" + SAMLXMLNS + ":NameID>");
    }
    start = requestData.indexOf("<" + SAMLXMLNS + ":SubjectConfirmation", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for saml:SubjectConfirmation ");
    }
    end = requestData.indexOf("/>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for SubjectConfirmation");
    }
    int mystart = requestData.indexOf(subjectconf, start);
    if (mystart == -1) {
      throw new Exception("Did not find expected subjectconfirmation for saml, expected " + subjectconf);
    }
    start = requestData.indexOf("<" + SAMLXMLNS + ":Conditions", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for Conditions ");
    }
    start = requestData.indexOf("<" + SAMLXMLNS + ":AuthnStatement", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for AuthnStatement ");
    }
    end = requestData.indexOf("</" + SAMLXMLNS + ":AuthnStatement>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for AuthnStatement");
    }
    start = requestData.indexOf("<" + SAMLXMLNS + ":AuthnContext>", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for " + "<" + SAMLXMLNS + ":AuthnContext> ");
    }
    end = requestData.indexOf("</" + SAMLXMLNS + ":AuthnContext>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for AuthnStatement");
    }
    start = requestData.indexOf("<" + SAMLXMLNS + ":AuthnContextClassRef>", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for " + "<" + SAMLXMLNS + ":AuthnContextClassRef> ");
    }
    end = requestData.indexOf("</" + SAMLXMLNS + ":AuthnContextClassRef>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for " + "<" + SAMLXMLNS + ":AuthnContextClassRef>");
    }
  }


  private void handleExpectedFaultInResponse() throws Exception {


    int start = 0;
    String message_faultcode;
    String message_faultstring;
    start = responseData.indexOf("<faultcode", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for <faultcode ");
    }
    int end = responseData.indexOf("</faultcode>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for <faultcode>");
    }

    start += "<faultcode".length() + responseData.substring(start).indexOf(">") + 1;
    message_faultcode = responseData.substring(start, end);

    trace("message_faultcode " + message_faultcode);
    start = 0;
    start = responseData.indexOf("<faultstring>", start);
    if (start == -1) {
      throw new Exception("Did not find start tag for <faultstring> ");
    }
    end = responseData.indexOf("</faultstring>", start);
    if (end == -1) {
      throw new Exception("Did not find end tag for <faultstring>");
    }
    start += "<faultstring>".length();
    message_faultstring = responseData.substring(start, end);

    trace("message_faultstring " + message_faultstring);
    throw new Exception("Failed with faultcode " + message_faultcode + " end message  " + message_faultstring);

  }

  private static String DSIG_RSASHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
  private static String DSIG_HMACSHA256 = "http://www.w3.org/2001/04/xmldsig-more#hmac-sha256";

  private void handleSingnatureMethod(String soapMessage) throws Exception {
    System.out.println("*******handleSingnatureMethod begin*********");
    Node node = null;
    ByteArrayInputStream input = new ByteArrayInputStream(
        soapMessage.getBytes());
    XPath xpath = XPathFactory.newInstance().newXPath();
    InputSource inputSource = new InputSource(input);
    NodeList nodelist = (NodeList) xpath.evaluate("//*[local-name()='SignatureMethod']", inputSource,
        XPathConstants.NODESET);
    if (nodelist == null || nodelist.getLength() < 1) {
      throw new Exception(
          "Did not find SignatureMethod element in the message");
    }
    for (int i = 0; i < nodelist.getLength(); i++) {
      Node oneNode = nodelist.item(i);
      NamedNodeMap attrMaps = oneNode.getAttributes();
      Node algoNode = attrMaps.getNamedItem("Algorithm");
      if (algoNode == null) {
        throw new Exception(
            "Did not find Algorithm of SignatureMethod in the message");
      }
      String algorithm = algoNode.getNodeValue();
      if (!(DSIG_RSASHA256.equalsIgnoreCase(algorithm) || DSIG_HMACSHA256
          .equals(algorithm))) {
        throw new Exception(
            "The algorithm of SignatureMethod should be RSA-SHA256 and HMAC-SHA256 but got "
                + algorithm);
      }
    }
    System.out.println("*******" + nodelist.getLength() + " handleSingnatureMethod OK*********");
    input.close();
  }


  private String createSoapFaultStr(Exception e) {
    String s = "<env:Envelope xmlns:env=\"http://schemas.xmlsoap.org/soap/envelope/\"><env:Header/><env:Body><env:Fault><faultcode>env:Server</faultcode><faultstring>Exception in senderVouches:SoapMsgFilter " + e + "</faultstring></env:Fault></env:Body></env:Envelope>";
    return s;
  }

  private String createSoapFaultStrWithMessage(String message_faultstring, String message_faultcode) {
    String s = "<env:Envelope xmlns:env=\"http://schemas.xmlsoap.org/soap/envelope/\"><env:Header/><env:Body><env:Fault xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\"><faultcode>" + message_faultcode + "</faultcode><faultstring>Exception in senderVouches:SoapMsgFilter " + message_faultstring + "</faultstring></env:Fault></env:Body></env:Envelope>";
    return s;
  }
}


