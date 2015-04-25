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
    private SimpleXYSeries _seriesFiltered;
    private SimpleXYSeries _seriesPeaks;
    private SimpleXYSeries _seriesFFT;

    public HeartRateMonitor(XYPlot plot) {
        _bufferRaw = new CircularBuffer(128);
        _bufferFiltered = new CircularBuffer(128);
        _plot = plot;

        // Configure our plot accordingly
        for (XYSeries series : _plot.getSeriesSet()) {
            if ("Camera Data".equals(series.getTitle())) {
                _seriesFiltered = (SimpleXYSeries)series;
            }
            else if ("Peaks".equals(series.getTitle())) {
                _seriesPeaks = (SimpleXYSeries)series;
            }
            else if ("FFT".equals(series.getTitle())) {
                _seriesFFT = (SimpleXYSeries)series;
            }
        }
        if (_seriesFiltered == null) {
            _seriesFiltered = new SimpleXYSeries("Camera Data");
            _seriesFiltered.useImplicitXVals();
            _plot.addSeries(_seriesFiltered, new LineAndPointFormatter(Color.BLACK, Color.BLUE, null, null));
        }
        if (_seriesPeaks == null) {
            _seriesPeaks = new SimpleXYSeries("Peaks");
            _seriesPeaks.useImplicitXVals();
            _plot.addSeries(_seriesPeaks, new LineAndPointFormatter(null, Color.YELLOW, null, null));
        }
        if (_seriesFFT == null) {
            _seriesFFT = new SimpleXYSeries("FFT");
            //_plot.addSeries(_seriesFFT, new BarFormatter(Color.GREEN, Color.BLACK));
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

            // Update graph data
            if (_bufferFiltered.size() == _seriesFiltered.size()) {
                _seriesFiltered.removeFirst();
                _seriesPeaks.removeFirst();
            }
            _seriesFiltered.addLast(null, values[FILTER_NEIGHBORS]);
            _seriesPeaks.addLast(null, null);
            if (_bufferFiltered.size() > 1 &&
                    (_bufferFiltered.getValue(0) < _bufferFiltered.getValue(1) &&
                    !(_bufferFiltered.getValue(1) < _bufferFiltered.getValue(2)))) {
                // We found a peak!  Record it.
                _seriesPeaks.setY(_bufferFiltered.getValue(1), _seriesPeaks.size()-2);
            }
            _plot.redraw();
        }
    }

    public Pair<Double, Double> getRate() {
        // Prepare peaks result
        double peaksVal = -1;
        if (_bufferFiltered.size() > 100) {
            // Count up peaks in our series data
            int peaks = 0;
            for (int i = 0; i < _seriesPeaks.size(); i++) {
                if (_seriesPeaks.getY(i) != null) {
                    peaks++;
                }
            }

            // Adjust the crossing count by our shortened time span to get BPM
            peaksVal = (peaks * (60000 / _bufferFiltered.timeSpan()));
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

            // Prep the graph, if we've not done it yet
            float indices = (idxMax - idxMin + 2);
            if (_seriesFFT.size() != indices) {
                while (_seriesFFT.size() > 0) {
                    _seriesFFT.removeFirst();
                }
                for (int i = 0; i < indices; i++) {
                    _seriesFFT.addLast(127.f * (i / indices), null);
                }
            }

            // Process components within above ranges to find the most likely bpm!
            int fftMagMaxIdx = -1;
            double fftMagMax = 0;
            for (int i = idxMin; i <= idxMax; i++) {
                double fftMag = Math.sqrt(Math.pow(fftReal[i], 2) + Math.pow(fftImaginary[i], 2));
                if (fftMagMaxIdx == -1 || fftMagMax < fftMag) {
                    fftMagMaxIdx = i;
                    fftMagMax = fftMag;
                }
                _seriesFFT.setY(fftMag, i-idxMin);
            }
            fftVal = fft.indexToFreq(fftMagMaxIdx) * 60.f;
        }

        return Pair.create(peaksVal, fftVal);
    }
}
