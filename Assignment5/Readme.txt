CSE P 590: Ubiquitous Computing (Spring 2015)
Assignment 5 - Microcontrollers and Sensors
Jeff Weiner (jdweiner@cs.washington.edu)

https://github.com/AtOMiCNebula/UW-CSEP590-UbiComp/tree/master/Assignment5


This assignment had us use our prior knowledge of building a pulse counter
to build one that ran on a microcontroller/tablet solution.  Neat!

We use an RFduino (pulseduino.ino) to parse input signal data from a pulse
sensor, filter and batch it up on the microprocessor, and then periodically
send it via BTLE over to an android tablet.  I use GPIO pin 2 for the pulse
sensor input, since I was having trouble with getting good data from pin 1.

The android tablet performs minimal data crunching, after which it displays
the data realtime.  Most of the crunching is really just ensuring repeated
data sent over from the microcontroller are not double-counted.

pulseduino.ino: The RFduino code, which manages data reading, filtering, and
                transmission.
MainActivity.java: Main view code, no real pulse-specific code
HeartRateMonitor.java: Android-side pulse processing
