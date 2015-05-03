#!/usr/bin/env python

# Import python numerical processing libraries
from numpy import *
import time

print "Running forever: mash CTRL-C in the terminal to quit"

# Keep track of the last sample we read in
read_idx = 0
while True:
	# mmap data so we don't have to hold the whole thing in memory
	data = memmap("output.float32", dtype=float32)

	# Assume we're running at 2M samples/sec
	fs = 2e6

	# If we have more than a quarter of a second of new data, process it!
	if len(data) - read_idx > fs/4:
		# We will plot the last second of data every 1/4 second
		read_idx += fs/4

		# Be mindful of edge conditions; we don't necessarily have a full second of data
		start_idx = max(read_idx - fs, 0)
		t = arange(start_idx, read_idx)/fs
	else:
		# Sleep for 100ms after releasing our data handle
		data = None
		time.sleep(0.1)
