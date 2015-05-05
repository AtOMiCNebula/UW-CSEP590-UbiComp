CSE P 590: Ubiquitous Computing (Spring 2015)
Assignment 3 - Software Defined Radio: Decoding RF Signals
Jeff Weiner (jdweiner@cs.washington.edu)

https://github.com/AtOMiCNebula/UW-CSEP590-UbiComp/tree/master/Assignment3


This assignment had us take raw analog signal data from GNU Radio (and a
software radio unit) and display information about which of four buttons was
being pressed on a 315Mhz transmitter.  Fun stuff!

I started my program from the provided plot_float32_continuous.py.  After
taking out the plotting code (keeping the continuous memmap loops), I added
four steps to process and refine the data into something ready to display:

1.  Analog Signal -> Digital Bits/Packets (Groups of Zeroes and Ones)
First, given that GNU Radio outputted one million samples per second (1Mhz
sample rate), I ignored all but every 100th sample, the same strategy the
provided plotting code used.  Once that basic filter was applied, any time
the signal rose over a threshold magnitude (0.1), I counted it as "high".
Depending on how long that signal stayed high (either side of 600 samples), I
counted it as a 0 (if less than) or a 1 (if more than).  Additionally, if
sufficient time went by without seeing another bit, my program considered the
"packet" complete, and started a fresh packet the next time a bit was seen.

2.  Digital Bits/Packets -> Button Presses
Now that we decoded packets from our analog signal, the next step was to
interpret them.  The transmitter always used the same preamble, a (0,1) pattern
repeated eight times.  Then, the following eight bits represented the button
that was pressed.  Then, a trailing zero, to end the signal.

3.  Button Presses -> State Changes
With raw button presses coming in, I then applied some filtering to, within a
given time window, attempt to cut out repeated button impuleses down into just
"press" and "release" records.

4.  State Changes -> Display to Console
After the button impulses had been filtered down, most of what remained was to
just display the data.  Before that was done, some last-minute filtering was
done, to make sure we don't output repeated "press" records when a press
spanned multiple 0.25s windows.

I'm really pleased with how this assignment turned out!  I've never had a
chance to play with a SDR unit before, and it was really interesting.  Now that
they're so cheap, I might have to buy one for myself for further research. :)

assignment3.py:
	parse_packets: step #1 above
	decode_buttons: step #2 above
	analyze_buttons: step #3 above
	while True loop: step #4 above
