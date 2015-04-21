package works.com.hellovision2;

import com.badlogic.gdx.audio.analysis.FFT;


public class HeartRateMonitor {
    private CircularBuffer _buffer;

    public HeartRateMonitor() {
        _buffer = new CircularBuffer(128);
    }

    public void newCameraAverage(float red) {
        _buffer.add(red);
    }

    public double getRate() {
        double result = -1;
        if (_buffer.size() == 128) {
            // Prepare data for our FFT
            float[] fftData = new float[128];
            for (int i = 0; i < _buffer.size(); i++) {
                fftData[_buffer.size()-i-1] = _buffer.getValue(i);
            }
            float fftRate = (1000 * _buffer.size()) / (_buffer.getTime(0) - _buffer.getTime(_buffer.size()-1));

            // Create the FFT, and give it the data we prepared!
            FFT fft = new FFT(128, fftRate);
            fft.forward(fftData);

            // Grab real/imaginary components
            float[] fftReal = fft.getRealPart();
            float[] fftImaginary = fft.getImaginaryPart();

            // Get our boundary indices
            int idxMin = fft.freqToIndex(.5f); // .5hz (30bpm) should be a safe lower-bound
            int idxMax = fft.freqToIndex(3.f); // 3hz (180bpm) should be a safe upper-bound

            // Process components within above ranges to find the most likely bpm!
            int fftMagMaxIdx = -1;
            double fftMagMax = 0;
            for (int i = idxMin; i <= idxMax; i++) {
                double fftMag = Math.sqrt(Math.pow(fftReal[i], 2) + Math.pow(fftImaginary[i], 2));
                if (fftMagMaxIdx == -1 || fftMagMax < fftMag) {
                    fftMagMaxIdx = i;
                    fftMagMax = fftMag;
                }
            }
            result = fft.indexToFreq(fftMagMaxIdx) * 60.f;
        }
        return result;
    }
}
