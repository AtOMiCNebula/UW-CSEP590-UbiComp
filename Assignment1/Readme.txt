CSE P 590: Ubiquitous Computing (Spring 2015)
Assignment 1 - Introduction to Mobile Android Development: Step Counter
Jeff Weiner (jdweiner@cs.washington.edu)

https://github.com/AtOMiCNebula/UW-CSEP590-UbiComp/tree/master/Assignment1

This assignment had us write a simple step-counting app to introduce us to
Android development.  My app takes the raw accelerometer data, runs it through
a median filter (radius of 5), de-means the data (over a rolling window of last
200 samples), and then looks for positive zero-crossings over a set threshold.

The axis data received from the accelerometer is immediately turned into a
magnitude, so the position one holds their tablet in shouldn't make a
difference.  I tested it both by placing my tablet in a cargo pocket, as well
as holding the tablet in my hand at various angles, and received successful
results each time.  It tends to be a little jumpy when being jostled around,
likely due to me not having it wait for ten or so consecutive steps before
beginning recording (like a Fitbit would), but during actual motion, it's
pretty close!  I also have a reset button, and the step count persists properly
over device orientation changes too.

I didn't add a graphical plot over time, as I was travelling both for work and
for Easter these past two weeks, so I didn't have a lot of spare time.  This
wasn't required, but all the same, I hope to be able to add a few more bells
and whistles for the next project. :)

MainActivity.java:
    Android "activity" code, very little step-counting specific code here.
StepCounter.java:
    This is where the accelerometer data is received, filtered, and sent out.
CircularBuffer.java:
    A very simple circular buffer I use to store (un)filtered data.
