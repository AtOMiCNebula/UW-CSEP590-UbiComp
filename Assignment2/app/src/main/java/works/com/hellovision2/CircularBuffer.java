package works.com.hellovision2;

public class CircularBuffer {
    private double[] _arr;
    private int _size;
    private int _nextWrite;

    public CircularBuffer(int capacity) {
        _arr = new double[capacity];
        _size = 0;
        _nextWrite = 0;
    }

    public void add(double value) {
        _arr[_nextWrite] = value;
        _size = Math.min(_size + 1, _arr.length);
        _nextWrite = ((_nextWrite + 1) % _arr.length);
    }

    public int size() {
        return _size;
    }

    // Expose a most-recent-first view (_arr[0] is most fresh, _arr[_arr.size()-1] is most stale)
    public double getValue(int i) {
        int idx = (_arr.length + _nextWrite - 1 - i) % _arr.length;
        return _arr[idx];
    }
}
