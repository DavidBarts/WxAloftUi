<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="webapp" tagdir="/WEB-INF/tags" %>
<%@ page language="java" import="info.koosah.wxaloftuiservlet.ContactBean" %>
<jsp:useBean id="contact"
  scope="page" class="info.koosah.wxaloftuiservlet.ContactBean" />
<%
  if (!contact.processRequest(request, response, application))
    return;
%>
<webapp:page>
  <jsp:attribute name="head">
    <meta charset="utf-8" />
    <title>Koosah.INFO: Contact</title>
    <style type="text/css">
      .error { color: red }
    </style>
    <script src="https://www.google.com/recaptcha/api.js"></script>
  </jsp:attribute>

  <jsp:body>
    <h1>Enter your message below</h1>
    <form action="contact.jsp" method="POST">
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
  </jsp:body>
</webapp:page>
