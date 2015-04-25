package works.com.hellovision2;

import android.graphics.Color;
import android.util.Pair;

import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.badlogic.gdx.audio.analysis.FFT;

import java.util.Arrays;


public class HeartRateMonitor {
    private static int FILTER_NEIGHBORS = 5; // median filter looks for 5 elements in either direction

    private CircularBuffer _bufferRaw;
    private CircularBuffer _bufferFiltered;
    private XYPlot _plot;
    private SimpleXYSeries _seriesZC;
    private SimpleXYSeries _seriesZCMean;

    public HeartRateMonitor(XYPlot plot) {
        _bufferRaw = new CircularBuffer(128);
        _bufferFiltered = new CircularBuffer(128);
        _plot = plot;

        // Configure our plot accordingly
        for (XYSeries series : _plot.getSeriesSet()) {
            if ("Zero Crossings".equals(series.getTitle())) {
                _seriesZC = (SimpleXYSeries)series;
            }
            else if ("ZC Mean".equals(series.getTitle())) {
                _seriesZCMean = (SimpleXYSeries)series;
            }
        }
        if (_seriesZC == null) {
            _seriesZC = new SimpleXYSeries("Zero Crossings");
            _seriesZC.useImplicitXVals();
            _plot.addSeries(_seriesZC, new LineAndPointFormatter(Color.BLUE, Color.BLACK, null, null));
        }
        if (_seriesZCMean == null) {
            _seriesZCMean = new SimpleXYSeries("ZC Mean");
            _plot.addSeries(_seriesZCMean, new LineAndPointFormatter(Color.BLACK, Color.BLACK, null, null));
        }
    }

    public void newCameraAverage(float red) {
        _bufferRaw.add(red);

        if (_bufferRaw.size() >= (FILTER_NEIGHBORS*2+1)) {
            float values[] = new float[FILTER_NEIGHBORS*2+1];
            for (int i = 0; i < FILTER_NEIGHBORS*2+1; i++) {
                values[i] = _bufferRaw.getValue(i);
            }
            Arrays.sort(values);
            _bufferFiltered.add(values[FILTER_NEIGHBORS]);

            // Update Series-ZC
            if (_bufferFiltered.size() == _seriesZC.size()) {
                _seriesZC.removeFirst();
            }
            _seriesZC.addLast(null, values[FILTER_NEIGHBORS]);
            _plot.redraw();
        }
    }

    public Pair<Double, Double> getRate() {
        // Prepare zero-crossings result
        double zcVal = -1;
        if (_bufferFiltered.size() > 100) {
            // De-mean data (using all median data, which will change over time as the buffer circles around)
            double mean = 0.0f;
            for (int i = 0; i < _bufferFiltered.size(); i++) {
                mean += _bufferFiltered.getValue(i);
            }
            mean /= _bufferFiltered.size();
            if (_seriesZCMean.size() != 2) {
                _seriesZCMean.addFirst(0, mean);
                _seriesZCMean.addFirst(127, mean);
            }
            else {
                _seriesZCMean.setXY(0, mean, 0);
                _seriesZCMean.setXY(127, mean, 1);
            }

            // Count up zero-crossings
            int crossings = 0;
            double checkPrevious = _bufferFiltered.getValue(0) - mean;
            for (int i = 1; i < _bufferFiltered.size(); i++) {
                double checkCurrent = _bufferFiltered.getValue(i) - mean;
                if (checkPrevious < 0 && checkCurrent > 0) {
                    crossings++;
                }
                checkPrevious = checkCurrent;
            }

            // Adjust the crossing count by our shortened time span to get BPM
            zcVal = (crossings * (60000 / _bufferFiltered.timeSpan()));
        }

        // Prepare FFT result
        double fftVal = -1;
        if (_bufferRaw.size() == 128) {
            // Create the FFT, and give it the data
            FFT fft = new FFT(128, _bufferRaw.sampleRate());
            float[] fftData = new float[fft.timeSize()];
            for (int i = 0; i < fftData.length; i++) {
                fftData[fftData.length-i-1] = _bufferRaw.getValue(i);
            }
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
            fftVal = fft.indexToFreq(fftMagMaxIdx) * 60.f;
        }

        return Pair.create(zcVal, fftVal);
    }
}
