// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package example;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import weblogic.transaction.Transaction;
import weblogic.transaction.TransactionHelper;
import weblogic.transaction.TransactionManager;

/**
 * Servlet implementation class TxForward
 */
@WebServlet("/TxForward")
public class TxForward extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private TransactionManager tm = (TransactionManager) 
      TransactionHelper.getTransactionHelper().getTransactionManager();

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    PrintWriter out = response.getWriter();

    String urlsParam = request.getParameter("urls");
    if (urlsParam == null) return;
    String[] urls = urlsParam.split(",");

    try {
      RemoteSync remoteSync = (RemoteSync) 
          new InitialContext().lookup(RemoteSync.JNDINAME);
      tm.begin();
      Transaction tx = (Transaction) tm.getTransaction();
      out.println("<pre>");
      out.println(Utils.getLocalServerID() + " started " + 
          tx.getXid().toString());
//      out.println(remoteSync.register());
      out.println(Utils.getLocalServerID() + " invoking registerAndForward() urls=" + Arrays.toString(urls));
      out.println(remoteSync.registerAndForward(urls));
      tm.commit();
      out.println(Utils.getLocalServerID() + " committed " + tx);
    } catch (NamingException | NotSupportedException | SystemException | 
        SecurityException | IllegalStateException | RollbackException | 
        HeuristicMixedException | HeuristicRollbackException e) {
      throw new ServletException(e);
    } 
	}
	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doGet(request, response);
	}

}
