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

  /* tool tips. see:
     https://stackoverflow.com/questions/17064913/display-tooltip-in-canvas-graph */
  var ttCanvas = document.createElement('canvas');
  ttCanvas.id = "ttCanvas";
  ttCanvas.width = 100;
  ttCanvas.height = 25;
  ttCanvas.style = "background-color:white;position:absolute;left:-200px;top:100px;"
  var ttCtx = ttCanvas.getContext("2d");
  map.parent.appendChild(ttCanvas);
  var canvasOffset = $("#mapCanvas").offset();
  var offsetX = canvasOffset.left;
  var offsetY = canvasOffset.top;
  $("#mapCanvas").mousemove(function(e) {
    var mouseX = parseInt(e.clientX - offsetX);
    var mouseY = parseInt(e.clientY - offsetY);
    var hit = false;
    for (var i = 0; i < locations.length; i++) {
        var dot = map.locationPoint(locations[i]);
        var dx = mouseX - dot.x;
        var dy = mouseY - dot.y;
        if (dx * dx + dy * dy < rsquared) {
            ttCanvas.style.left = (dot.x) + "px";
            ttCanvas.style.top = (dot.y - 40) + "px";
            ttCtx.clearRect(0, 0, ttCanvas.width, ttCanvas.height);
            var message = obs[i].temperature + "°C @ " + obs[i].altitude + "'";
            ttCtx.fillText(message, 5, 15);
            hit = true;
        }
    }
    if (!hit) {
        ttCanvas.style.left = "-200px";
    }
  });

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
