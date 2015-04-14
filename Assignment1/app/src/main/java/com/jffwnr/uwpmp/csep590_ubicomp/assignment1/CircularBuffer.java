package com.jffwnr.uwpmp.csep590_ubicomp.assignment1;

public class CircularBuffer {
    private Tuple[] _arr;
    private int _size;
    private int _nextWrite;

    public CircularBuffer(int capacity) {
        _arr = new Tuple[capacity];
        _size = 0;
        _nextWrite = 0;
    }

    public void add(long timestamp, double value) {
        _arr[_nextWrite] = new Tuple(timestamp, value);
        _size = Math.min(_size + 1, _arr.length);
        _nextWrite = ((_nextWrite + 1) % _arr.length);
    }

    public int size() {
        return _size;
    }

    // Expose a most-recent-first view (_arr[0] is most fresh, _arr[_arr.size()-1] is most stale)
    private int transformIndex(int i) {
        return (_arr.length + _nextWrite - 1 - i) % _arr.length;
    }

    public long getTimestamp(int i) {
        return _arr[transformIndex(i)]._timestamp;
    }

    public double getValue(int i) {
        return _arr[transformIndex(i)]._value;
    }


    private class Tuple {
        public long _timestamp;
        public double _value;

        public Tuple(long timestamp, double value) {
            _timestamp = timestamp;
            _value = value;
        }
    }
}
