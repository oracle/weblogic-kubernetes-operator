package apps.httpsessionreptestapp;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import weblogic.servlet.internal.session.RSID;
import weblogic.servlet.internal.MembershipControllerImpl;
import weblogic.servlet.spi.WebServerRegistry;
import java.util.Enumeration;

public class CounterServlet extends HttpServlet {    
  private String message;
  
  public CounterServlet() {
    setServletName(this.getClass().getName()); 
  }
  
  public void doGet(HttpServletRequest request,HttpServletResponse response)
		throws IOException, ServletException {        
    process(request,response);  
  }

  public void doPost(HttpServletRequest request,HttpServletResponse response)
		throws IOException, ServletException {  
    process(request,response);  
  }

  protected void process(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException    { 
	
    response.setContentType("text/xml; charset=UTF-8");
    PrintWriter out = response.getWriter();
    HttpSession currentSession = request.getSession(true);
      
    if (currentSession != null) {
      out.println("<result>"); 
      
      if (request.getParameter("invalidate") != null) {
        currentSession.invalidate();
        out.println("Your session is invalidated");
      } else {
        if (request.getParameter("setCounter") != null) {
          currentSession.setAttribute("count", Integer.valueOf(request.getParameter("setCounter")));
        } else if (request.getParameter("getCounter") != null ) {
          currentSession.setAttribute("count", ((Integer) currentSession.getAttribute("count")));
        } else if (request.getParameter("setCounter") == null && currentSession.isNew()) {
          currentSession.setAttribute("count", new Integer(1));
        } else {
          int count = ((Integer) currentSession.getAttribute("count")).intValue();
          currentSession.setAttribute("count", new Integer(++count));
        }
      
        out.println("<sessioncreatetime>"+currentSession.getCreationTime()+"</sessioncreatetime>");
        out.println("<sessionid>"+currentSession.getId()+"</sessionid>"); 
        out.println("<primary>"+getPrimaryServer(currentSession.getId())+"</primary>"); 
        out.println("<secondary>"+getSecondaryServer(currentSession.getId())+"</secondary>"); 
        out.println("<countattribute>"+currentSession.getAttribute("count")+"</countattribute>"); 
      }
      
      out.println("</result>");
    } else {
      out.println("<result>"); 
      out.println("<primary>NA</primary>"); 
      out.println("<secondary>NA</secondary>"); 
      out.println("<countattribute>00</countattribute>");
      out.println("</result>");
    }
  }

  private String getPrimaryServer(String sessionId) {
    MembershipControllerImpl cluster = 
      (MembershipControllerImpl) WebServerRegistry.getInstance().getClusterProvider();
    RSID rsid = new RSID(sessionId, cluster.getClusterMembers());
    
    if (rsid.getPrimary()== null) {
      return "No Primary";
    }
    
    return rsid.getPrimary().getServerName();
  }

  private String getSecondaryServer(String sessionId) {
    MembershipControllerImpl cluster = 
      (MembershipControllerImpl) WebServerRegistry.getInstance().getClusterProvider();
    RSID rsid = new RSID(sessionId, cluster.getClusterMembers());
    
    if (rsid.getSecondary() == null ) {
      return "No Secondary";
    }
    
    return rsid.getSecondary().getServerName();
  }
  
  public String getServletName() {
    return this.message;
  }

  private void setServletName(String message) {
    this.message=message;
  }
}

