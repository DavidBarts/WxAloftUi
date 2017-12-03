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
  </head>

  <body>
    <!-- need zoom and pan controls (links) -->
    <form method="GET" action="obs_demo.jsp">
      <input type="hidden" name="area" value="${obsDemo.areaId}" />
      <c:if test="${not empty param.since}">
        <input type="hidden" name="since" value="${param.since}" />
      </c:if>
      <c:if test="${not empty param.north}">
        <input type="hidden" name="north" value="${param.north}" />
      </c:if>
      <c:if test="${not empty param.south}">
        <input type="hidden" name="south" value="${param.south}" />
      </c:if>
      <c:if test="${not empty param.east}">
        <input type="hidden" name="east" value="${param.east}" />
      </c:if>
      <c:if test="${not empty param.west}">
        <input type="hidden" name="west" value="${param.west}" />
      </c:if>
      <img src="/WxAloftUi/GetMap${fn:escapeXml(obsDemo.mapParams)}"
        alt="usemap" border="0" usemap="#observations" />
      <map name="observations">
        <c:forEach var="observation" items="${obsDemo.observations}">
          <area shape="circle" coords="${observation.x},${observation.y},${obsDemo.radius}"
            href="obs_demo_detail.jsp?id=${observation.id}"
            title="${observation.details}" />
        </c:forEach>
      </map>
    </form>
    <!-- may need pan down control here -->
  </body>
</html>
