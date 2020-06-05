// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package example;

import java.io.IOException;
import java.io.PrintWriter;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import weblogic.transaction.Transaction;
import weblogic.transaction.TransactionHelper;
import weblogic.transaction.TransactionManager;

@WebServlet("/TxPropagate")
public class TxPropagate extends HttpServlet {
  private static final long serialVersionUID = 7100799641719523029L;
  private TransactionManager tm = (TransactionManager) 
      TransactionHelper.getTransactionHelper().getTransactionManager();

  protected void doGet(HttpServletRequest request, 
      HttpServletResponse response) throws ServletException, IOException {
    PrintWriter out = response.getWriter();

    String urlsParam = request.getParameter("urls");
    if (urlsParam == null) {
      return;
    }
    String[] urls = urlsParam.split(",");

    try {
      RemoteSync forward = (RemoteSync) 
          new InitialContext().lookup(RemoteSync.JNDINAME);
      tm.begin();
      Transaction tx = (Transaction) tm.getTransaction();
      out.println("<pre>");
      out.println(Utils.getLocalServerID() + " started "
          + tx.getXid().toString());
      //out.println(forward.register());
      for (int i = 0; i < urls.length; i++) {
        out.println(Utils.getLocalServerID() + " " + tx.getXid().toString()
            + " registering Synchronization on " + urls[i]);
        Context ctx = Utils.getContext(urls[i]);
        forward = (RemoteSync) ctx.lookup(RemoteSync.JNDINAME);
        out.println(forward.register());
      }
      tm.commit();
      out.println(Utils.getLocalServerID() + " committed " + tx);
    } catch (NamingException | NotSupportedException | SystemException
        | SecurityException | IllegalStateException | RollbackException
        | HeuristicMixedException | HeuristicRollbackException e) {
      throw new ServletException(e);
    }
  }
}
