<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="webapp" tagdir="/WEB-INF/tags" %>
<webapp:page>
  <jsp:attribute name="head">
    <meta charset="utf-8" />
    <title>Koosah.INFO</title>
  </jsp:attribute>
  <jsp:body>
    <h1>Welcome to Koosah.INFO</h1>

    <h2>What’s Here?</h2>

    <h3><a href="obs.jsp?area=KSEA">Real-Time Upper Atmosphere Weather</a></h3>
    <p>This is a map detailing upper-atmosphere weather observations made by
    automated recording equipment aboard commercial aircraft in the
    Seattle area. It’s interactive; hover the mouse pointer over the colored
    dots that represent observations to see the details of that observation.

    <h3><a href="obsm.jsp?area=KSEA">Mobile Device Version</a></h3>
    <p>This page will probably work better on most mobile devices than the
    previous one. If your device supports hovering, the observation details
    should pop up in tool-tip messages. If not, clicking on the dots will bring
    up the observation details in a separate page.</p>

    <h3><a href="obst.jsp?area=KSEA">All-Text Version</a></h3>
    <p>This is an all-text page that displays the observations in tabular
    format.</p>

    <h2>About Koosah.INFO</h2>
    <p>More information about this site may be found
    <a href="about.jsp">here.</a></p>

    <h2>Contact</h2>
    <p>You can send me a message <a href="contact.jsp">here.</a>

    <h2>Disclaimer</h2>
    <p><strong>Important:</strong> The information presented on this site is
    here for educational purposes only, and is <em>not</em> suitable for any
    sort of mission-critical use. Please read the
    <a href="about.jsp">about Koosah</a> page for more information.</p>
  </jsp:body>
</webapp:page>
