<%@ tag description="Overall Page template" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ attribute name="onload" %>
<%@ attribute name="head" fragment="true" %>
<html>
  <head>
    <style type="text/css">
      body { font-family: Helvetica, Arial, sans-serif }
    </style>
    <jsp:invoke fragment="head"/>
  </head>
  <c:choose>
    <c:when test="${empty pageScope.onload}"><body></c:when>
    <c:otherwise><body onload="${fn:escapeXml(pageScope.onload)}"></c:otherwise>
  </c:choose>
    <div style="overflow-x: hidden">
      <a href="/WxAloftUi"><img style="left: 50%" src="header_wide.png" alt="Koosah.INFO"/></a>
    </div>
    <jsp:doBody/>
  </body>
</html>
