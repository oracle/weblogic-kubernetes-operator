// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package com.examples.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.examples.pof.Contact;
import com.examples.pof.ContactId;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;

/**
 * This web application is used for following with respect to Coherence cache depending on user
 * action. 1. Populating the cahce using "add" , first name and second name used for populating the
 * data. 2. To get the cache size using "count" 3. Clear the cache using "clear" 4. Get all cached
 * data details using "get"
 */
@WebServlet(
    name = "CoherenceApp",
    urlPatterns = {"/CoherenceApp"})
public class CoherenceApp extends HttpServlet {

  private ArrayList keyList = new ArrayList<ContactId>();

  public CoherenceApp() {
    super();
  }

  /**
   * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  protected void processRequest(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/html;charset=UTF-8");
    PrintWriter out = response.getWriter();

    String action = request.getParameter("action");
    String firstName = request.getParameter("first");
    String secondName = request.getParameter("second");

    // Call respective methods based on user action
    if (action.equals("add")) {
      add(request, response);
    } else if (action.equals("size")) {
      count(request, response);
    } else if (action.endsWith("clear")) {
      clear(request, response);
    } else if (action.endsWith("get")) {
      get(request, response);
    }
  }

  /*
   * Clea the cache and updates the size after cache
   */
  private void clear(HttpServletRequest request, HttpServletResponse response) throws IOException {
    NamedCache cache = CacheFactory.getCache("contacts");
    cache.clear();
    PrintWriter out = response.getWriter();
    if (cache.size() == 0) {
      out.println("Cache is cleared and current size is :" + cache.size());
    } else {
      out.println("Cache is not cleared and current size is :" + cache.size());
    }
  }

  /*
   * Returns the current cache size
   */
  private void count(HttpServletRequest request, HttpServletResponse response) throws IOException {
    NamedCache cache = CacheFactory.getCache("contacts");
    PrintWriter out = response.getWriter();
    out.println(cache.size());
  }

  /*
   * Add first name and second name as cache data
   */
  private void add(HttpServletRequest request, HttpServletResponse response) throws IOException {
    NamedCache cache = CacheFactory.getCache("contacts");
    String firstName = request.getParameter("first");
    String secondName = request.getParameter("second");
    PrintWriter out = response.getWriter();
    Contact contact = new Contact(firstName, secondName);

    ContactId contactID = new ContactId(firstName, secondName);
    keyList.add(contactID);
    cache.put(contactID, contact);
    Contact contactGet = (Contact) cache.get(contactID);
    out.println("\nContact added:" + contactGet);
  }

  /*
   * Retrieve all cached data as of now
   */
  private void get(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    NamedCache cacheMap = CacheFactory.getCache("contacts");
    Set keys = cacheMap.keySet();
    PrintWriter out = response.getWriter();
    Iterator iterate = keys.iterator();
    while (iterate.hasNext()) {
      ContactId key = (ContactId) iterate.next();
      Contact contactGet = (Contact) cacheMap.get(key);
      out.println(contactGet);
    }
  }

  // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on
  // the + sign on the left to edit the code.">
  /**
   * Handles the HTTP <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "Short description";
  } // </editor-fold>
}
