CSE P 590: Ubiquitous Computing (Spring 2015)
Assignment 2 - Sampling and Processing Sensor Data: Optical Heart Rate Monitor
Jeff Weiner (jdweiner@cs.washington.edu)

https://github.com/AtOMiCNebula/UW-CSEP590-UbiComp/tree/master/Assignment2

Submitted 4 days late, after being granted 7 extra (thank you!)


This assignment had us use camera data to attempt to discern a heart rate,
and also graphs that data using a plotting library.  I apply median filtering
to cut down on noise from the camera sensor, and peak detection is used to
count beats.  Raw filter data is also passed to a FFT to attempt to determine
a heart rate, and the values are displayed alongside each other for comparison.

I had initially gotten tripped up trying to get better data out of the FFT
algorithm, but eventually gave up after realizing the FFT just wasn't going to
be as precise as I had hoped given the data rates we were receiving from the
camera (~15fps).  I then tried to switch to zero-crossing over median-filtered
data, but found out that peak detection (instead of zero-crossing) was better,
so gave up on counting zero-crossings.  I didn't leave zero-crossing reporting
in like I did for the FFT.

Tested on a Nexus 7 (2013).  The rear camera is a little wobbly as it gets
started.  Sometimes it feels like the hardware is trying to perform some
auto-brightness/-gain compensation, which of course messes with the data graph
and such.  It eventually stabilizes, however, and it's easy to see this on the
graph when it does.  Sometimes it takes 15~20 seconds to reach that point
though...I couldn't figure out a way to try to disable this correction through
the Android SDK.  Hopefully it wasn't something obvious. :)

Open the app, and tap "GO!" to begin data recording/analysis.

VisionActivity.java:
    Android "activity" code, very little rate-measuring specific code here.
HeartRateMonitor.java:
    Camera data is processed here, both for peak detection, as well as FFTing.
CircularBuffer.java:
    A very simple circular buffer I use to store data, adapted from my previous
    assignment.
