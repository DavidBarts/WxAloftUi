<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ page language="java" import="name.blackcap.wxaloftuiservlet.RContactBean" %>
<jsp:useBean id="contact"
  scope="page" class="name.blackcap.wxaloftuiservlet.RContactBean" />
<%
  if (!contact.processRequest(request, response, application))
    return;
%>
<html>
  <head>
    <title>David W. Barts: Contact</title>
    <style type="text/css">
      .error { color: red }
    </style>
    <script src="https://www.google.com/recaptcha/api.js"></script>
  </head>

  <body>
    <h1>Enter your message below</h1>
    <form action="rcontact.jsp" method="POST">
      <p>
        Your e-mail address (required):<br/>
        <input type="text" name="address" size="80" maxlength="255"
          value="${fn:escapeXml(param['address'])}"/>
        <c:if test="${contact.badAddress}">
          <br/>
          <span class="error">Invalid e-mail address.</span>
        </c:if>
        <c:if test="${contact.missingAddress}">
          <br/>
          <span class="error">Please enter your e-mail address above.</span>
        </c:if>
      </p><p>
        Subject (optional):<br/>
        <input type="text" name="subject" size="80" maxlength="255"
          value="${fn:escapeXml(param['subject'])}"/>
      </p><p>
        Message (required):<br/>
        <textarea name="message" rows="24" cols="80"><c:out value="${param['message']}"/></textarea>
        <c:if test="${contact.missingMessage}">
          <br/>
          <span class="error">Please enter your message above.</span>
        </c:if>
      </p><p>
        Please prove you are not a bot:<br/>
        <div class="g-recaptcha" data-sitekey="${initParam['recaptcha.key.site']}"></div>
        <c:if test="${contact.badCaptcha}">
          <span class="error">You must prove you are not a bot.</span>
        </c:if>
      </p><p style="text-align: center">
        <input type="submit" value="Send Message"/>
      </p>
    </form>
    <c:if test="${not empty contact.forward}">
      <jsp:forward page="${contact.forward}"/>
    </c:if>
  </body>
</html>
