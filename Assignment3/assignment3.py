#!/usr/bin/env python

# Import python numerical processing libraries
from datetime import datetime,timedelta
from numpy import *
import time

print "Running forever: mash CTRL-C in the terminal to quit"

# Constants
fs = 1e6    # Assume we're running at 1M samples/sec
skipRate = 100    # Only consider every 100th sample
limit_bit = 0.002    # time separation between bits
limit_packet = 0.12    # time separation between packets
thresh_bitHeight = 0.1    # height a signal needs to be considered
thresh_widthOne = 600    # samples needed to count as 1 (vs. 0)

# Data Segments
# Typical format: PREAMBLE + DATA + 0
# Note: pattern may abruptly cut off, either because of window cutoff, or
#       transmission end (the unit does not guarantee complete patterns)
packet_preamble = [ False, True, False, True, False, True, False, True,
                    False, True, False, True, False, True, False, True ]
packet_dataA = [ False, False, False, False, False, False, True, True ]
packet_dataB = [ False, False, False, False, True, True, False, False ]
packet_dataC = [ False, False, True, True, False, False, False, False ]
packet_dataD = [ True, True, False, False, False, False, False, False ]

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
				packet_countdown = limit_bit*fs/skipRate
				idx_bitStart = None

			# If the packet has gotten too stale, it's done
			if packet_countdown > 0:
				packet_countdown -= 1
			elif packet_countdown is not None:
				packets.append({ 'idx': i, 'data': packet })
				packet = []
				packet_countdown = None

	return packets

def decode_buttons(packets):
	result = []

	for packet in packets:
		button = None
		preamble = packet['data'][:len(packet_preamble)]
		if preamble == packet_preamble:
			data = packet['data'][len(packet_preamble):][:len(packet_dataA)]
			if data == packet_dataA:
				button = 'A'
			elif data == packet_dataB:
				button = 'B'
			elif data == packet_dataC:
				button = 'C'
			elif data == packet_dataD:
				button = 'D'

		if button is not None:
			result.append({ 'idx': packet['idx'], 'button': button })

	return result

def stale_idx(idx):
	return idx + limit_packet*fs/skipRate

def analyze_buttons(buttons):
	result = []

	last_button = None
	last_idx = None
	for button in buttons:
		# Send buttonup if the button changed (must have been quick!),
		# or if we timed out
		depress = False
		if last_button and last_button is not button['button']:
			depress = True
		elif last_idx and stale_idx(last_idx) < button['idx']:
			depress = True
		if depress:
			result.append({ 'button': last_button, 'pressed': False, 'idx': min(button['idx'], stale_idx(last_idx)) })
			last_button = None
			last_idx = None

		# Send buttondown, if we haven't already!
		last_idx = button['idx']
		if last_button is not button['button']:
			last_button = button['button']
			result.append({ 'button': last_button, 'pressed': True, 'idx': last_idx })

	# Send buttonup if we timed out with no subsequent button!
	idx_end = fs/4/skipRate
	if last_idx and stale_idx(last_idx) < idx_end:
		result.append({ 'button': last_button, 'pressed': False, 'idx': stale_idx(last_idx) })

	return result

dump_startTime = datetime.now()
def print_button(idx, button, buttondown):
	t = dump_startTime + timedelta(milliseconds=idx/10)
	print "%s: Button %s %s" % (t, button, ("Pressed" if buttondown else "Released"))

# Keep track of the last sample we read in
last_button = None
read_idx = 0
while True:
	# mmap data so we don't have to hold the whole thing in memory
	data = memmap("output.float32", dtype=float32)

	# If we have more than a quarter of a second of new data, process it!
	if len(data) - read_idx > fs/4:
		start_idx = read_idx
		read_idx += fs/4

		packets = parse_packets(data[start_idx:read_idx:skipRate])
		buttons = decode_buttons(packets)
		state_changes = analyze_buttons(buttons)

		if last_button is not None and len(state_changes) == 0:
			# We should have seen a buttondown!  Timing must have been lucky.
			print_button(start_idx/skipRate, last_button['button'], False)
			last_button = None
			continue

		if last_button and state_changes[0]['button'] == last_button['button']:
			# The first button hit this time was the same as the last button
			# we saw hit.  Were they within a close enough range that we
			# consider them part of the same press?
			if stale_idx(last_button['idx']) < (state_changes[0]['idx']+(read_idx-start_idx)/skipRate):
				# Too slow!  We're just seeing rapid presses.
				print_button(start_idx/skipRate, last_button['button'], False)
				last_button = None
			else:
				# Looks like a long hold!  Drop it from our list.
				state_changes = state_changes[1:]

		# Now that we've initially filtered the results, display the rest
		for change in state_changes:
			print_button(start_idx/skipRate+change['idx'], change['button'], change['pressed'])
			last_button = change if change['pressed'] else None

		# We're about to get a new window.  At this point, if we have a
		# last_button, we need to carry it over.  Set its idx to be the
		# maximum index value, so that we can continue it properly for
		# the next window.
		if last_button:
			last_button['idx'] = (read_idx-start_idx)/skipRate

	else:
		# Sleep for 100ms after releasing our data handle
		data = None
		time.sleep(0.1)
