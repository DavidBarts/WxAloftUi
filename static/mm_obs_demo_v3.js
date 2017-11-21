var drawMap = function(obs) {
  var RADIUS = 4;  /* radius of the dots we draw */
  var rsquared = RADIUS * RADIUS;
  var provider = new com.modestmaps.TemplatedLayer('http://tile.openstreetmap.org/{Z}/{X}/{Y}.png');
  var map = new com.modestmaps.Map('map', provider);
  var canvas = document.createElement('canvas');
  canvas.id = "mapCanvas";
  canvas.style.position = 'absolute';
  canvas.style.left = '0';
  canvas.style.top = '0';
  canvas.width = map.dimensions.x;
  canvas.height = map.dimensions.y;
  map.parent.appendChild(canvas);

  /* set map extent to encompass all our observations */
  var locations = [];
  for (var i=0; i<obs.length; i++)
    locations.push(new com.modestmaps.Location(obs[i].latitude, obs[i].longitude));
  map.setExtent(locations);

  /* make text to pretty-print a single field value */
  function listIt(name, value, suffix) {
    if (value == null)
      return name + ": (missing)";
    else
      return name + ": " + value + suffix;
  }

  /* printing multi-line messages. see:
     http://junerockwell.com/end-of-line-or-line-break-in-html5-canvas/ */
  function multiLineText(ctx, msgs) {
      var fontSize = parseInt(window.getComputedStyle(ctx.canvas, null).getPropertyValue("font-size"));
      var yIncr = Math.round(fontSize * 1.1);
      var numLines = Math.min(Math.trunc(ctx.canvas.height/yIncr), msgs.length);
      var x = fontSize;
      var y = Math.round((ctx.canvas.height - yIncr * (numLines-1)) / 2.0);
      for (var i=0; i<numLines; i++) {
          ctx.fillText(msgs[i], x, y);
          y += yIncr;
      }
  }

  /* tool tips. see:
     https://stackoverflow.com/questions/17064913/display-tooltip-in-canvas-graph */
  var ttCanvas = document.createElement('canvas');
  ttCanvas.id = "ttCanvas";
  ttCanvas.width = 220;
  ttCanvas.height = 135;
  ttCanvas.style.backgroundColor = "white";
  ttCanvas.style.border = "1px solid black";
  ttCanvas.style.position = "absolute";
  ttCanvas.style.left = "-800px";
  ttCanvas.style.top = "100px";
  var ttCtx = ttCanvas.getContext("2d");
  map.parent.appendChild(ttCanvas);
  var canvasOffset = $("#mapCanvas").offset();
  var offsetX = canvasOffset.left;
  var offsetY = canvasOffset.top;
  var touched = false;
  function ttHide() {
    ttCanvas.style.left = "-800px";
  }
  $("#mapCanvas").mousemove(function(e) {
    if (touched) {
      ttHide();
      return;
    }
    var mouseX = parseInt(e.clientX - offsetX);
    var mouseY = parseInt(e.clientY - offsetY);
    var hit = false;
    for (var i = 0; i < locations.length; i++) {
      var dot = map.locationPoint(locations[i]);
      var dx = mouseX - dot.x;
      var dy = mouseY - dot.y;
      if (dx * dx + dy * dy < rsquared) {
        ttCanvas.style.left = (dot.x) + "px";
        ttCanvas.style.top = (dot.y + 20) + "px";
        ttCtx.clearRect(0, 0, ttCanvas.width, ttCanvas.height);
        var message = [ listIt("Temperature", obs[i].temperature, "°C"),
          listIt("Altitude", obs[i].altitude, " ft"),
          listIt("Wind direction", obs[i].wind_dir, "°"),
          listIt("Wind speed", obs[i].wind_speed, " kn"),
          listIt("Time observed", obs[i].observed, ""),
          listIt("Time received", obs[i].received, ""),
          listIt("Frequency", obs[i].frequency, " MHz"),
          listIt("Source", obs[i].source, "") ];
        multiLineText(ttCtx, message);
        hit = true;
      }
    }
    if (!hit)
      ttHide();
  });

  /* touching is a way for mobile device users to get around the fact that
     they can't hover. */
  function handleTouch(e) {
    touched = true;
    ttHide();
    e.preventDefault();
    var mouseX = parseInt(e.targetTouches[0].clientX - offsetX);
    var mouseY = parseInt(e.targetTouches[0].clientY - offsetY);
    for (var i = 0; i < locations.length; i++) {
      var dot = map.locationPoint(locations[i]);
      var dx = mouseX - dot.x;
      var dy = mouseY - dot.y;
      if (dx * dx + dy * dy < rsquared)
        alert(obs[i].temperature + "°C @ " + obs[i].altitude + "' @ " + obs[i].received);
    }
  }
  canvas.addEventListener("touchstart", handleTouch, false);

  /* for generating colors based on altitude */
  function hex(v) {
    var ret = Math.trunc(v).toString(16);
    if (ret.length == 1)
      ret = "0" + ret;
    return ret;
  }

  function getColor(altitude) {
    var MIN = 0;
    var MAX = 40000;
    altitude = Math.min(MAX, Math.max(MIN, altitude));
    var mid = (MIN + MAX) / 2;
    if (altitude <= mid)
      return "#" + hex(0xff * altitude / mid) + "ff00";
    else
      return "#ff" + hex(0xff * (1.0 - (altitude-mid)/mid)) + "00";
  }

  /* the map gets drawn here */
  function redraw() {
    var ctx = canvas.getContext('2d');
    ctx.clearRect(0,0,canvas.width,canvas.height);
    ctx.strokeStyle = '#404040';
    for (var i=1; i<locations.length; i++) {
      var p = map.locationPoint(locations[i]);
      ctx.beginPath();
      ctx.fillStyle = getColor(obs[i].altitude);
      ctx.arc(p.x, p.y, RADIUS, 0.0, 2.0*Math.PI, true);
      ctx.fill();
    }
  }

  map.addCallback('drawn', redraw);
  map.addCallback('resized', function() {
    canvas.width = map.dimensions.x;
    canvas.height = map.dimensions.y;
    redraw();
  });

  redraw();
};

var initMap = function() {
  $.getJSON("http://koosah.info/WxAloftApi/ObsDemo", drawMap)
  .fail(function() { alert("getJson failed!"); });
};
