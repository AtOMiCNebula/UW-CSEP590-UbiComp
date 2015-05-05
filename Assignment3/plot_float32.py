#!/usr/bin/env python

# Import python numerical processing libraries
from numpy import *
from scipy import *
from pylab import *

# Load in data in float32 format
data = fromfile("output.float32", float32)
print "Loaded %d samples"%(len(data))

# Assume we're running at 1M samples/sec
fs = 1e6
t = arange(len(data))/fs

# Display the data to the user
plot(t, data)
xlabel("Time (s)")
ylabel("Magnitude")
show()
