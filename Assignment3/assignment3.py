#!/usr/bin/env python

# Import python numerical processing libraries
from numpy import *
import time

print "Running forever: mash CTRL-C in the terminal to quit"

# Constants
fs = 2e6    # Assume we're running at 2M samples/sec
skipRate = 100    # Only consider every 100th sample
thresh_packet = 0.002    # min time separation between packets
thresh_bitHeight = 0.1    # height a signal needs to be considered
thresh_widthOne = 600    # samples needed to count as 1 (vs. 0)

def parse_packets(data):
	packets = []

	packet = []
	packet_countdown = None
	idx_bitStart = None
	for i in range(len(data)):
		if thresh_bitHeight < data[i]:
			if idx_bitStart is None:
				# Sample just crossed above the threshold
				idx_bitStart = i
		else:
			if idx_bitStart is not None:
				# Sample just crossed below the threshold
				one = (thresh_widthOne/skipRate) < (i - idx_bitStart)
				packet.append(one)
				packet_countdown = thresh_packet*fs/skipRate
				idx_bitStart = None

			# If the packet has gotten too stale, it's done
			if packet_countdown > 0:
				packet_countdown -= 1
			elif packet_countdown is not None:
				packets.append({ 'idx': i, 'data': packet })
				packet = []
				packet_countdown = None

	return packets

# Keep track of the last sample we read in
read_idx = 0
while True:
	# mmap data so we don't have to hold the whole thing in memory
	data = memmap("output.float32", dtype=float32)

	# If we have more than a quarter of a second of new data, process it!
	if len(data) - read_idx > fs/4:
		# We will plot the last second of data every 1/4 second
		read_idx += fs/4

		# Be mindful of edge conditions; we don't necessarily have a full second of data
		start_idx = max(read_idx - fs, 0)
		t = arange(start_idx, read_idx)/fs

		packets = parse_packets(data[start_idx:read_idx:skipRate])
		print packets
	else:
		# Sleep for 100ms after releasing our data handle
		data = None
		time.sleep(0.1)
