<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ page language="java" import="name.blackcap.wxaloftuiservlet.ObsDemoBean" %>
<jsp:useBean id="obsDemo"
  scope="page" class="name.blackcap.wxaloftuiservlet.ObsDemoBean" />
<%
  if (!obsDemo.processRequest(request, response))
    return;
%>
<html>
  <head>
    <title>JSP Observations Demo</title>
    <style type="text/css">
      table, th, td { border: none }
      th, td { text-align: center }
      table { width: 100% }
      .obsmap {
        display: block;
        margin-left: auto;
        margin-right: auto;
        border: 1px solid black
      }
    </style>
  </head>

  <body>
    <h1><c:out value="${obsDemo.shortArea}"/> Weather Observatons</h1>
    <p>This map shows observations taken by aircraft in the
    <c:out value="${obsDemo.longArea}"/> area since
    <c:out value="${obsDemo.since}"/>. Hover over or click on
    the dots (color-coded by elevation) to see the details of each
    observation.</p>
    <!-- need zoom and pan controls (links) -->
    <img src="/WxAloftUi/GetMap${fn:escapeXml(obsDemo.mapParams)}"
      alt="Map may take a moment to load, hang on..." usemap="#observations"
      class="obsmap" />
    <map name="observations">
      <c:forEach var="observation" items="${obsDemo.observations}">
        <area shape="circle" coords="${observation.x},${observation.y},${obsDemo.radius}"
          href="obs_demo_detail.jsp?area=${obsDemo.areaId}&amp;id=${observation.id}"
          title="${observation.details}" />
      </c:forEach>
    </map>
    <!-- may need pan down control here -->
  </body>
</html>
