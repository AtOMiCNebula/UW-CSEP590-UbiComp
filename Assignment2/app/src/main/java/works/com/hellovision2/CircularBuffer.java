package works.com.hellovision2;

public class CircularBuffer {
    private final float[] _arrValues;
    private final long[] _arrTimes;
    private int _size;
    private int _nextWrite;

    public CircularBuffer(int capacity) {
        _arrValues = new float[capacity];
        _arrTimes = new long[capacity];
        _size = 0;
        _nextWrite = 0;
    }

    public void add(float value) {
        synchronized (_arrValues) {
            _arrValues[_nextWrite] = value;
            _arrTimes[_nextWrite] = System.currentTimeMillis();
            _size = Math.min(_size + 1, _arrValues.length);
            _nextWrite = ((_nextWrite + 1) % _arrValues.length);
        }
    }

    public int size() {
        synchronized (_arrValues) {
            return _size;
        }
    }

    // Expose a most-recent-first view (_arr[0] is most fresh, _arr[_arr.size()-1] is most stale)
    public float getValue(int i) {
        synchronized (_arrValues) {
            int idx = (_arrValues.length + _nextWrite - 1 - i) % _arrValues.length;
            return _arrValues[idx];
        }
    }

    public long getTime(int i) {
        synchronized (_arrValues) {
            int idx = (_arrTimes.length + _nextWrite - 1 - i) % _arrTimes.length;
            return _arrTimes[idx];
        }
    }
}
