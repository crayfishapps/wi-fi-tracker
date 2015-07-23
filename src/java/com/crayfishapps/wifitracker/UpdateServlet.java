package com.crayfishapps.wifitracker;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 *
 * @author crayfishapps developer
 */
public class UpdateServlet extends HttpServlet {
    
    private DatastoreService datastore;
    
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain");
       
        datastore = DatastoreServiceFactory.getDatastoreService();
        
        PrintWriter out = response.getWriter();
        
        try {
            Enumeration<String> parameterNames = request.getParameterNames();
            if (parameterNames.hasMoreElements()) {
                String serialNumberAdapter = request.getParameter("adapter");
                serialNumberAdapter = serialNumberAdapter.trim().toLowerCase();
                serialNumberAdapter = serialNumberAdapter.replaceAll(":", "");
                String macAddress = request.getParameter("macaddress");
                macAddress = macAddress.trim().toLowerCase();
                macAddress = macAddress.replaceAll(":", "");
                String operator = request.getParameter("operator");
                
                boolean inputError = false;
                
                if ((serialNumberAdapter.length() != 12) || (!serialNumberAdapter.matches("^[a-f0-9]*$"))){
                    inputError = true;
                }
                if ((macAddress.length() != 12) || (!macAddress.matches("^[a-f0-9]*$"))){
                    inputError = true;
                }
                
                if (!inputError) {
                    boolean whiteList = false;
                    boolean blackList = false;
                        
                    // check if the adapter is registered with the user
                    String userID = null;
                    String userMail = null;
                    String notification = null;

                    Filter adapterFilter = new FilterPredicate("serialnumber", Query.FilterOperator.EQUAL, serialNumberAdapter);
                    Query adapterQuery = new Query("Adapter").setFilter(adapterFilter);
                    List<Entity> members = datastore.prepare(adapterQuery).asList(FetchOptions.Builder.withDefaults());
                    if (!members.isEmpty()) {
                        for (Entity memberEntity : members) {
                            userID =  memberEntity.getProperty("userID").toString();
                            userMail =  memberEntity.getProperty("userMail").toString();
                            notification =  memberEntity.getProperty("notification").toString();
                        }
                    }
                    
                    // check if the MAC address is listed
                    if (userID != null) {
                        Filter userFilter = new FilterPredicate("userID", Query.FilterOperator.EQUAL, userID);
                        Query addressQuery = new Query("MACAddress").setFilter(userFilter);
                        members = datastore.prepare(addressQuery).asList(FetchOptions.Builder.withDefaults());
                        if (!members.isEmpty()) {
                            for (Entity memberEntity : members) {
                                String listedItem = memberEntity.getProperty("serialnumber").toString();
                                String listType = memberEntity.getProperty("listtype").toString(); 
                                if (listedItem.equals(macAddress)) {
                                    if (listType.equals("w")) {
                                        whiteList = true;
                                    }
                                    else {
                                        blackList = true;
                                    }
                                }
                            }
                        }
                        
                        // add item to database
                        if (!whiteList) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            Date date = new Date();
                            String dateString = dateFormat.format(date);                

                            if (operator.equals("add")) {
                                Entity member = new Entity("Unit");
                                member.setProperty("adapter", serialNumberAdapter);
                                member.setProperty("macaddress", macAddress);
                                member.setProperty("registered", dateString);
                                member.setProperty("removed", "");
                                datastore.put(member);
                            } else {
                                Filter adapterFilterUnit = new FilterPredicate("adapter", Query.FilterOperator.EQUAL, serialNumberAdapter);
                                Filter macaddressFilterUnit = new FilterPredicate("macaddress", Query.FilterOperator.EQUAL, macAddress);
                                Filter unitFilter = CompositeFilterOperator.and(adapterFilterUnit, macaddressFilterUnit);
                                Query macaddressQuery = new Query("Unit").setFilter(unitFilter).addSort("registered", SortDirection.DESCENDING);
                                members = datastore.prepare(macaddressQuery).asList(FetchOptions.Builder.withDefaults());
                                if (!members.isEmpty()) {
                                    Entity member = members.get(0);
                                    String removedString = member.getProperty("removed").toString();
                                    if (removedString.isEmpty()) {
                                        member.setProperty("removed", dateString);
                                    }
                                    datastore.put(member);
                                }
                            }
                        }

                        // send notification
                        if (operator.equals("add")) {
                            if (notification.equals("d") && whiteList == false) {
                                sendMessage(userMail, serialNumberAdapter, macAddress);
                            }
                            if (notification.equals("b") && blackList == true) {
                                sendMessage(userMail, serialNumberAdapter, macAddress);
                            }
                        }
                    }                    
                }
            }
            out.println("<OK>");
        }
        catch (Exception e) {
        }
        finally {
            out.close();
        }
    }
       
    public void sendMessage(String userMail, String adapter, String macaddress) {
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties, null);

        String msgBody = "The Wi-Fi Tracker " + adapter + " has discovered a unit.\n";
        msgBody += "The MAC address of the detected unit is " + macaddress;

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress("wi-fi-tracker@appspot.gserviceaccount.com", "wi-fi-tracker.appspot.com Admin"));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(userMail, "wi-fi-tracker.appspot.com User"));
            msg.setSubject("Wi-Fi Tracker Notification");
            msg.setText(msgBody);
            Transport.send(msg);

        } catch (Exception e) {
        }
    }
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
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
    }// </editor-fold>

}
