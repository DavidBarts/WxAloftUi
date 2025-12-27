<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="webapp" tagdir="/WEB-INF/tags" %>
<%@ page language="java" import="info.koosah.wxaloftuiservlet.ObsmBean" %>
<jsp:useBean id="obs"
  scope="page" class="info.koosah.wxaloftuiservlet.ObsmBean" />
<%
  if (!obs.processRequest(request, response))
    return;
%>
<webapp:page>
  <jsp:attribute name="head">
    <meta charset="utf-8" />
    <title>Koosah.INFO: Aircraft Weather Observations</title>
    <meta name="robots" content="nofollow"/>
    <meta name="viewport" content="width=device-width, initial-scale=1" />
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
        border: 1px solid black;
        -webkit-user-select: none;
        -ms-user-select: none;
        user-select: none
      }
    </style>
  </jsp:attribute>

  <jsp:body>
    <h1><c:out value="${obs.shortArea}"/> Weather Observations</h1>
    <p>The map below shows observations taken by aircraft in the
    <c:out value="${obs.longArea}"/> area since
    <c:out value="${obs.since}"/>. Hover over, click on, or tap on
    the dots (colour-coded by elevation) to see the details of each
    observation.</p>

    <p>If you do not see any coloured dots, it means there are no observations
    with the specified time frame (by default, the last two hours). Try
    using the selector above the map to go back further in time.</p>

    <p>This map can be zoomed and panned. Zoom in by clicking on the plus sign
    in the upper-right corner, zoom out by clicking on the minus sign in the
    upper-left corner, and pan by clicking on the arrow on the appropriate edge.
    (If you are at a limit, one or more of these operations will be disallowed;
    initially, the only thing you can do is zoom in.)</p>

    <p>If you are on a mobile device, this page is best viewed in landscape
    mode (i.e. with your phone held sideways).</p>

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
              <area shape="circle" coords="${observation.x},${observation.y},${obs.radius*2}"
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
    <p>Map <a href="https://www.openstreetmap.org/copyright">© OpenStreetMap</a> contributors.</p>
  </jsp:body>
</webapp:page>
