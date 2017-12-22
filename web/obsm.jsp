<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ page language="java" import="name.blackcap.wxaloftuiservlet.ObsmBean" %>
<jsp:useBean id="obs"
  scope="page" class="name.blackcap.wxaloftuiservlet.ObsmBean" />
<%
  if (!obs.processRequest(request, response))
    return;
%>
<html>
  <head>
    <title>Aircraft Weather Observations</title>
    <meta name="robots" content="nofollow"/>
    <style type="text/css">
      table, th, td { border: none }
      .left { text-align: left }
      .center { text-align: center }
      .right { text-align: right }
      .middle { vertical-align: middle }
      .thirds { width: 33.33% }
      .nav { text-decoration: none }
      .obsmap {
        display: block;
        margin-left: auto;
        margin-right: auto;
        border: 1px solid black
      }
    </style>
  </head>

  <body>
    <h1><c:out value="${obs.shortArea}"/> Weather Observatons</h1>
    <p>This map shows observations taken by aircraft in the
    <c:out value="${obs.longArea}"/> area since
    <c:out value="${obs.since}"/>. Hover over or click on
    the dots (color-coded by elevation) to see the details of each
    observation.</p>

    <p>This map can be zoomed and panned. Zoom in by clicking on the plus sign
    in the upper-right corner, zoom out by clicking on the minus sign in the
    upper-left corner, and pan by clicking on the arrow on the appropriate edge.
    (If you are at a limit, one or more of these operations will be disallowed;
    initially, the only thing you can do is zoom in.)</p>

    <c:if test="${(empty param['zoom']) or (sessionScope['zoom'] eq param['zoom'])}">
     <jsp:include page="obs_since.jsp">
       <jsp:param name="action" value="obsm.jsp"/>
       <jsp:param name="since" value="${obs.rawDuration}"/>
       <jsp:param name="area" value="${obs.shortArea}"/>
     </jsp:include>
    </c:if>

    <p><a href="obsm.jsp?area=${obs.shortArea}">Reset this map.</a>
    <a href="/">Return to main page.</a></p>

    <table style="margin-left: auto; margin-right: auto">
      <tr>
        <td></td>
        <td class="center">
          <table style="width: 100%">
            <tr>
              <td class="thirds left">
                <%-- FIXME: these <c:if> blocks should probably be a custom
                     tag, since the pattern is used so much in this page. --%>
                <c:if test="${not empty obs.zoomOut}">
                  <a class="nav" href="${fn:escapeXml(obs.zoomOut)}">&#8722;</a>
                </c:if>
              </td><td class="thirds center">
                <c:if test="${not empty obs.panNorth}">
                  <a class="nav" href="${fn:escapeXml(obs.panNorth)}">↑</a>
                </c:if>
              </td><td class="thirds right">
                <c:if test="${not empty obs.zoomIn}">
                  <a class="nav" href="${fn:escapeXml(obs.zoomIn)}">+</a>
                </c:if>
              </td>
            </tr>
          </table>
        </td>
        <td></td>
      </tr><tr>
        <td class="right middle">
          <c:if test="${not empty obs.panWest}">
            <a class="nav" href="${fn:escapeXml(obs.panWest)}">←</a>
          </c:if>
        </td>
        <td class="center middle">
          <img src="/WxAloftUi/GetMap${fn:escapeXml(obs.mapParams)}"
            alt="Map may take a moment to load, hang on..." usemap="#observations"
            class="obsmap" />
          <map name="observations">
            <c:forEach var="observation" items="${obs.observations}">
              <area shape="circle" coords="${observation.x},${observation.y},${obs.radius}"
                href="obs_demo_detail.jsp?area=${obs.areaId}&amp;id=${observation.id}"
                title="${observation.details}" />
            </c:forEach>
          </map>
        </td>
        <td class="left middle">
          <c:if test="${not empty obs.panEast}">
            <a class="nav" href="${fn:escapeXml(obs.panEast)}">→</a>
          </c:if>
        </td>
      </tr><tr>
        <td></td>
        <td class="center">
          <c:if test="${not empty obs.panSouth}">
            <a class="nav" href="${fn:escapeXml(obs.panSouth)}">↓</a>
          </c:if>
        </td>
        <td></td>
      </tr>
    </table>
  </body>
</html>
