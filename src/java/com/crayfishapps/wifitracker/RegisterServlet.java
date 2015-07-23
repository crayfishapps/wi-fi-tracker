package com.crayfishapps.wifitracker;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author crayfishapps developer
 */
public class RegisterServlet extends HttpServlet {
    
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
        response.setContentType("text/html;charset=UTF-8");
        
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        datastore = DatastoreServiceFactory.getDatastoreService();
        
        String userString;
        
        if (user == null) {
            userString = "<p>Welcome!</p>";
            userString += "<p><a class=\"greenbutton\" href=\"" + userService.createLoginURL("/register") + "\">Sign in here</a></p>";
        }
        else {
            userString = "<p>Welcome, " + user.getNickname() + "</p>";
            userString += "<p><a class=\"greenbutton\" href=\"" + userService.createLogoutURL("/") + "\">Sign out here</a></p>";
        }
        
        PrintWriter out = response.getWriter();
        
        try {
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>Wi-Fi Adapter Registration</title>");
            out.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"main.css\"/>");
            out.println("</head>");
            out.println("<body>");
            out.println(userString);
            
            if (user != null) {
                
                out.println("<p><a class=\"greenbutton\" href=list>List registration</a></p>");
                
                String currentUser = user.getUserId();
                
                // Add new entry to datastore or remove entry
                Enumeration<String> parameterNames = request.getParameterNames();
                if (parameterNames.hasMoreElements()) {
                    String serialNumber = request.getParameter("serialnumber");
                    String information = request.getParameter("information");
                    String notification = request.getParameter("notification");
                    String action = request.getParameter("action");
                    
                    serialNumber = serialNumber.trim().toLowerCase();
                    serialNumber = serialNumber.replaceAll(":", "");
                    serialNumber = serialNumber.replaceAll("-", "");

                    if (action != null) {
                        removeItem(serialNumber);
                    }
                    else {
                        if ((serialNumber.length() != 12) || (!serialNumber.matches("^[a-f0-9]*$"))){
                            out.println("<p><div class=\"error\">The format of the serial number is incorrect!</div></p>");
                        }
                        else {
                            if(isSerialNumberRegistered(serialNumber)) {
                                out.println("<p><div class=\"error\">The serial number is already registered!</div></p>");
                            }
                            else {
                                Entity member = new Entity("Adapter");
                                
                                // main parameters
                                member.setProperty("userID", user.getUserId());
                                member.setProperty("userMail", user.getEmail());
                                member.setProperty("serialnumber", serialNumber);
                                
                                // additional informatin added by user
                                member.setProperty("information", information);
                                member.setProperty("notification", notification);
                                
                                datastore.put(member);

                                out.println("<p><div class=\"success\">The Wi-Fi adapter with serial number " + serialNumber + " has been added.</div></p>");
                            }
                        }
                    }
                }
                
                // Form to add a Wi-Fi adapter
                out.println("<form action=\"/register\" method=\"get\">");
                out.println("<div><fieldset>");
                out.println("<label for=\"serialnumber\">Wi-Fi Adapter: <em>required</em></label>");
                out.println("<input id=\"serialnumber\" type=\"text\" name=\"serialnumber\" class=\"textinput\"><br>");
                out.println("<label for=\"Information\">Information: </label>");
                out.println("<input id=\"information\" type=\"text\" name=\"information\" class=\"textinput\"><br>");               
                out.println("<label for=\"notification\">Notification: </label>");
                out.println("<input type=\"radio\" name=\"notification\" value=\"n\" checked>Never");
                out.println("<input type=\"radio\" name=\"notification\" value=\"d\">On detection");
                out.println("<input type=\"radio\" name=\"notification\" value=\"b\">On blacklist<br>");                
                out.println("<label>&nbsp;</label><input class=\"greenbutton\" type=\"submit\" value=\"Add Adapter\">");
                out.println("</fieldset></div>");
                out.println("</form><br>");

                // list of existing Wi-Fi adapters
                Filter userFilter = new FilterPredicate("userID", FilterOperator.EQUAL, currentUser);
                Query query = new Query("Adapter").setFilter(userFilter);
                List<Entity> members = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
                try {
                    if (!members.isEmpty()) {
                        int rawCount = 0;
                        out.println("<table>");
                        out.println("<tr><th>Serial number</th><th>Information</th><th>Notification</th><th>Action</th></tr>");
                        for (Entity memberEntity : members) {
                            rawCount++;

                            String serialNumber = memberEntity.getProperty("serialnumber").toString();
                            String information = memberEntity.getProperty("information").toString();
                            String notification = memberEntity.getProperty("notification").toString();
                            if (notification.equals("n")) {
                                notification = "Never";
                            }
                            if (notification.equals("d")) {
                                notification = "On detection";
                            }
                            if (notification.equals("b")) {
                                notification = "On blacklist";
                            }
                            
                            out.print("<tr");
                            if (rawCount % 2 == 0) {
                                out.println(" class=\"alt\"");
                            }
                            out.println(">");

                            out.println("<td>" + serialNumber + "</td>");
                            out.println("<td>" + information + "</td>");
                            out.println("<td>" + notification + "</td>");
                            out.print("<td>");                            
                            out.print("<a class=\"greenbutton\" href=\"/report?serialnumber=");
                            out.print(serialNumber + "\">Report</a>&nbsp;");
                            out.print("<a class=\"greenbutton\" href=\"/register?action=remove&serialnumber=");
                            out.print(serialNumber + "\">Remove</a>");
                            out.println("</td></tr>");                            
                        }
                        out.println("</table>");
                    }
                }
                catch (Exception e) {
                    out.println("<p>Error: " + e.getMessage() + "</p><p></p>");
                }
            
            }
            
            out.println("</body>");
            out.println("</html>");
        } finally {
            out.close();
        }
    }
    
    private boolean isSerialNumberRegistered(String serialNumber) {
        Filter filter = new FilterPredicate("serialnumber", FilterOperator.EQUAL, serialNumber);
        Query query = new Query("Adapter").setFilter(filter);
        List<Entity> members = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
        return !members.isEmpty();
    }
    
    private void removeItem(String serialNumber) {
        Filter filter = new FilterPredicate("serialnumber", FilterOperator.EQUAL, serialNumber);
        Query query = new Query("Adapter").setFilter(filter);
        List<Entity> members = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
        if (!members.isEmpty()) {
            for (Entity memberEntity : members) {
                datastore.delete(memberEntity.getKey());
            }
        }
        
        Filter adapterFilter = new Query.FilterPredicate("adapter", Query.FilterOperator.EQUAL, serialNumber);
        Query unitQuery = new Query("Unit").setFilter(adapterFilter);
        List<Entity> unitMembers = datastore.prepare(unitQuery).asList(FetchOptions.Builder.withDefaults());
        if (!unitMembers.isEmpty()) {
            for (Entity memberEntity : unitMembers) {
                datastore.delete(memberEntity.getKey());
            }
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
