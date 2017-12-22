<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<form action="${param['action']}" method="GET">
  <input type="hidden" name="area" value="${param['area']}"/>
  Show observations taken in the last:
  <select name="since">
    <c:forTokens items="PT1H,1 hour;PT1H30M,1.5 hours;PT2H,2 hours;PT2H30M,2.5 hours;PT3H,3 hours;PT3H30M,3.5 hours;PT4H,4 hours;PT4H30M,4.5 hours;PT5H,5 hours;PT5H30M,5.5 hours;PT6H,6 hours" delims=";" var="i">
      <c:set var="d" value="${fn:split(i, ',')}"/>
      <c:choose>
        <c:when test="${param['since'] eq d[0]}">
          <option value="${d[0]}" selected><c:out value="${d[1]}"/></option>
        </c:when>
        <c:otherwise>
          <option value="${d[0]}"><c:out value="${d[1]}"/></option>
        </c:otherwise>
      </c:choose>
    </c:forTokens>
  </select>
  <input type="submit" value="Go"/>
</form>
