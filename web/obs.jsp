<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ page language="java" import="name.blackcap.wxaloftuiservlet.ObsBean" %>
<jsp:useBean id="obs"
  scope="page" class="name.blackcap.wxaloftuiservlet.ObsBean" />
<%
  if (!obs.processRequest(request, response))
    return;
%>
<html>
  <head>
    <title>Aircraft Weather Observations</title>
    <style type="text/css">
      #map {
        width: 90vw;
        height: 90vw;
        margin-left: auto;
        margin-right: auto;
        border: 1px solid black
      }
    </style>
  </head>

  <body onload="initMap(${obs.areaId}, &quot;${obs.duration}&quot;)">
    <h1><c:out value="${obs.shortArea}"/> Weather Observatons</h1>
    <p>This map shows observations taken by aircraft in the
    <c:out value="${obs.longArea}"/> area since
    <c:out value="${obs.since}"/>. Hover over
    the dots (color-coded by elevation) to see the details of each
    observation. Use the mouse to pan and zoom.</p>

    <jsp:include page="obs_since.jsp">
      <jsp:param name="action" value="obs.jsp"/>
      <jsp:param name="since" value="${obs.duration}"/>
      <jsp:param name="area" value="${obs.shortArea}"/>
    </jsp:include>

    <div id="map" />
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js" type="text/javascript"></script>
    <script src="https://rawgithub.com/stamen/modestmaps-js/master/modestmaps.min.js" type="text/javascript"></script>
    <script src="obs.js" type="text/javascript"></script>
  </body>
</html>
