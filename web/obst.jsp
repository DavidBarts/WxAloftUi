<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="webapp" tagdir="/WEB-INF/tags" %>
<%@ page language="java" import="name.blackcap.wxaloftuiservlet.ObstBean" %>
<jsp:useBean id="obs"
  scope="page" class="name.blackcap.wxaloftuiservlet.ObstBean" />
<%
  if (!obs.processRequest(request, response))
    return;
%>
<webapp:page>
  <jsp:attribute name="head">
    <meta charset="utf-8" />
    <title>Koosah.INFO: Aircraft Weather Observations</title>
    <style type="text/css">
      table, th, td { border: solid black 1px }
    </style>
  </jsp:attribute>

  <jsp:body>
    <h1><c:out value="${obs.shortArea}"/> Weather Observatons</h1>
    <p>This table shows observations taken by aircraft in the
    <c:out value="${obs.longArea}"/> area since
    <c:out value="${obs.since}"/>. By default, it is sorted by time observed
    in ascending order. Click on a column heading to sort by that column
    instead. Click again on the same column heading to sort in descending
    order.<p>

    <jsp:include page="obs_since.jsp">
      <jsp:param name="action" value="obst.jsp"/>
      <jsp:param name="since" value="${obs.rawDuration}"/>
      <jsp:param name="area" value="${obs.shortArea}"/>
      <jsp:param name="order" value="${obs.order}"/>
    </jsp:include>

    <p><a href="/">Return to main page.</a></p>

    <table>
      <tr>
        <th><a href="${fn:escapeXml(obs.columns['observed'])}">Time Observed</a></th>
        <th><a href="${fn:escapeXml(obs.columns['received'])}">Time Received</a></th>
        <th><a href="${fn:escapeXml(obs.columns['altitude'])}">Altitude (ft)</a></th>
        <th><a href="${fn:escapeXml(obs.columns['latitude'])}">Latitude</a></th>
        <th><a href="${fn:escapeXml(obs.columns['longitude'])}">Longitude</a></th>
        <th><a href="${fn:escapeXml(obs.columns['temperature'])}">Temperature (˚C)</a></th>
        <th><a href="${fn:escapeXml(obs.columns['wind_dir'])}">Wind Direction</a></th>
        <th><a href="${fn:escapeXml(obs.columns['wind_speed'])}">Wind speed (kn)</a></th>
        <th><a href="${fn:escapeXml(obs.columns['frequency'])}">Frequency (MHz)</a></th>
        <th><a href="${fn:escapeXml(obs.columns['source'])}">Source</a></th>
      </tr>
      <c:forEach var="row" items="${obs.rows}">
        <tr>
          <td><c:out value="${row['observed']}"/></td>
          <td><c:out value="${row['received']}"/></td>
          <td><c:out value="${row['altitude']}"/></td>
          <td><c:out value="${row['latitude']}"/></td>
          <td><c:out value="${row['longitude']}"/></td>
          <td><c:out value="${row['temperature']}"/></td>
          <td><c:out value="${row['wind_dir']}"/></td>
          <td><c:out value="${row['wind_speed']}"/></td>
          <td><c:out value="${row['frequency']}"/></td>
          <td><c:out value="${row['source']}"/></td>
        </tr>
      </c:forEach>
    </table>
  </jsp:body>
</webapp:page>
