package com.oracle.kubernetes.examples;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GetListenAddressServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {

    String address = ListenAddressAndPort.getListenAddress();

    PrintWriter out = res.getWriter();
    out.println("<HTML>");
    out.println("<HEAD><TITLE>Get Listen Address V1</TITLE></HEAD>");
    out.println("<BODY>");
    out.println("<p><h1>Get Listen Address V1</h1>");
    out.println("<p>Server listen address is " + address + ".");
    out.println("</BODY></HTML>");
  }
}
