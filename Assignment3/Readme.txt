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

One thing I'm not quite pleased about...due to my design of step 3 and 4 being
separate, and not applying the filtering all at once, it forces a trade-off
between being able to identify rapid-fire presses, and very long holds.
Long holds can span more than one 0.25s window, for instance, and because
impulses are already filtered out by the time we get to step 4, we don't know
when the "last" one was, beyond somewhere within the last limit_packet portion
of the window.  Additionally, I had previously removed the code that had us
reprocess the same data mutliple times (i.e. processing 1 second worth of data,
with a sliding window of 0.25s), as it was more difficult than I expected to
correlate the same events in each window.  But then, that means we may chop off
part of a signal, so limit_packet had to be doubled, which makes it harder to
observe rapid-fire patterns...

That said, I'm okay submitting with this limitation, since it was already
beyond what was asked for the assignment.  So, take the results with that grain
of salt.  If you want to see a version of this change that works great with
rapid-fire button presses, step back one commit before this one in my GitHub
repository... O:-)

assignment3.py:
	parse_packets: step #1 above
	decode_buttons: step #2 above
	analyze_buttons: step #3 above
	while True loop: step #4 above
