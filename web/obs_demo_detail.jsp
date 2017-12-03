<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ page language="java" import="name.blackcap.wxaloftuiservlet.ObsDemoDetailBean" %>
<jsp:useBean id="obsDemoDetail"
  scope="page" class="name.blackcap.wxaloftuiservlet.ObsDemoDetailBean" />
<%
  if (!obsDemoDetail.processRequest(request, response))
    return;
%>
<html>
  <head>
    <title>Observation Details</title>
  </head>
  <body>
    <h1>Observation Details</h1>
    <pre>
<c:out value="${obsDemoDetail.details}" />
    </pre>
    <p><a href="javascript:history.back()">Back to map.</a>
  </body>
</html>
