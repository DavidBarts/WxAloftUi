<%@ page contentType="text/html; charset=UTF-8"  %>
<%@ taglib prefix="webapp" tagdir="/WEB-INF/tags" %>
<webapp:page>
  <jsp:attribute name="head">
    <meta charset="utf-8" />
    <title>Koosah.INFO: About</title>
  </jsp:attribute>
  <jsp:body>
    <h1>About Koosah.INFO</h1>

    <h2>Disclaimer</h2>
    <p>This is a site run by a hobbyist in his spare time. It contains data
    from coded transmissions (using undocumented codings) for which he
    believes he has reverse-engineered those codings. No subsequent quality
    control is done on the data received.</p>
    <p>Needless to say, <em>the data presented on this site are not suitable for
    any mission-critical purpose.</em> This site exists solely for the purpose
    of educating the public about upper-atmosphere weather and digital
    communications, <em>not</em> as a source of reliable, authenticated
    information for mission-critical applications.</p>

    <h2>What is this?</h2>
    <p>A site dedicated to (near) realtime upper air weather observations.</p>

    <h2>Why koosah?</h2>
    <p><em>Koosah</em> a common spelling of the
    <a href="https://en.wikipedia.org/wiki/Chinook_Jargon">Chinook Wawa</a>
    (aka Chinook Jargon) word for “sky.” Chinook Wawa is a Native American
    trade language that was once the most widely-spoken language in the
    Pacific Northwest. So this site’s name means “sky info.” Quite descriptive,
    I think.</p>

    <h2>How does this work?</h2>
    <p>Commercial jet aircraft have multiple radio transceivers (over a dozen, I
    believe!) in them. One of these transceivers sends and receives digital
    information called ACARS (aircraft communications addressing and reporting
    system). Commercial aircraft have weather sensors aboard, and most use ACARS
    to automatically send weather reports to ground stations.</p>
    <p>I have my own receivers that hear these transmissions, decode them,
    and send them to the server that provides this service.</p>

    <h2>Isn’t this information already available on the Internet?</h2>
    <p>Not for the general public. The airlines share this information with the
    National Oceanic and Atmospheric Administration (NOAA), but the airlines
    consider it proprietary and require NOAA to seriously limit access to it to
    the general public. In order to get NOAA to share the information with you,
    you must jump through a bureaucratic hoops and prove you have a legitimate
    scientific interest in it.</p>

    <h2>Feet <em>and</em> degrees Celsius? What’s with the crazy mixed units?</h2>
    <p>Believe it or not, that’s how the observations are reported! I think
    it’s strange, too. In the future there will be an option to choose between
    reporting things in consistently English, consistently metric, or native
    ACARS mixed units.</p>

    <h2>Why the limited coverage?</h2>
    <p>This is a hobby project of mine and I’m just getting started. Currently
    there’s only one receiving site (at my home, in Vancouver). I’ll be
    interested in adding more sites in the coming months. If you’re interested
    in hosting one, see next section.</p>

    <h2>How do I become a receiving site?</h2>
    <p>You have to have a reliable, always-on Internet connection and be willing
    to host a dedicated receiver that will regularly send small packets of data
    (one per ACARS message received) to my server. The receiver will need an
    external, above-the-roof (or at least in-attic) antenna, so you must live in
    a dwelling where this is both permissible and possible.</p>

  </jsp:body>
</webapp:page>
