package com.lannbox.rfduinotest;

import android.graphics.Color;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;


public class HeartRateMonitor {
    private int _lastTime;
    private XYPlot _plot;
    private SimpleXYSeries _seriesPeaks;

    public HeartRateMonitor(XYPlot plot) {
        _lastTime = 0;
        _plot = plot;

        // Configure our plot accordingly
        for (XYSeries series : _plot.getSeriesSet()) {
            if ("Peaks".equals(series.getTitle())) {
                _seriesPeaks = (SimpleXYSeries)series;
            }
        }
        if (_seriesPeaks == null) {
            _seriesPeaks = new SimpleXYSeries("Peaks");
            _plot.addSeries(_seriesPeaks, new LineAndPointFormatter(null, Color.BLUE, null, null));
        }
        _plot.setRangeBoundaries(0, 2, BoundaryMode.FIXED);
    }

    public void newBeatData(int[] data) {
        boolean foundNew = (_lastTime == 0);
        for (int dataValue : data) {
            if (foundNew) {
                _seriesPeaks.addLast(dataValue, 1);
                _lastTime = dataValue;
            }
            else if (dataValue == _lastTime) {
                foundNew = true;
            }
        }

        if (foundNew) {
            // Clean out old data
            int cutoff = _lastTime - 6000; // show 6s worth of data
            while (_seriesPeaks.size() > 0 && _seriesPeaks.getX(0).intValue() < cutoff) {
                _seriesPeaks.removeFirst();
            }

            // Update graph data
            _plot.setDomainBoundaries(cutoff - 500, _lastTime + 500, BoundaryMode.FIXED);
            _plot.redraw();
        }
    }

    public double getRate() {
        // Count up peaks in our series data
        int peaks = 0;
        for (int i = 0; i < _seriesPeaks.size(); i++) {
            peaks++;
        }

        // Adjust the crossing count by our shortened time span to get BPM
        double peaksVal = -1;
        if (peaks > 2) {
            double min = _seriesPeaks.getX(0).doubleValue(); // use double here for division below
            double max = _seriesPeaks.getX(_seriesPeaks.size()).doubleValue();
            peaksVal = (peaks * (60000 / (max - min)));
        }

        return peaksVal;
    }
}
