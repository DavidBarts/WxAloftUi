<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ page language="java" import="name.blackcap.wxaloftuiservlet.ObsDemoBean" %>
<html>
  <head>
    <c:choose>
      <c:when test="${empty param['error']}">
        <title>Koosah.INFO: Message Sent</title>
      </c:when>
      <c:otherwise>
        <title>Koosah.INFO: Message Not Sent</title>
      </c:otherwise>
    </c:choose>
  </head>

  <body>
    <c:choose>
      <c:when test="${empty param['error']}">
        <h1>Message Sent</h1>
        <p>Message sent successfully.</p>
      </c:when>
      <c:otherwise>
        <h1>Message Not Sent</h1>
        <p>Your message could not be sent due to the following error:</p>
        <p><c:out value="${param['error']}"/></p>
      </c:otherwise>
    </c:choose>
    <p><a href="/index.html">Back to home page.</a></p>
  </body>
</html>
