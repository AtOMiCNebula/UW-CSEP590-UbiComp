package works.com.hellovision2;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.androidplot.xy.XYPlot;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;


public class VisionActivity extends ActionBarActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    String TAG = "APP";
    CameraBridgeViewBase mOpenCvCameraView;

    private HeartRateMonitor _monitor = null;
    private FFTHandler _handler = null;
    private boolean _recording = false;
    private XYPlot _graph = null;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_vision);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        if (_monitor == null) {
            _monitor = new HeartRateMonitor((XYPlot)findViewById(R.id.xyPlot));
            _handler = new FFTHandler(this);
        }

        final Button button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _recording = true;
                button.setVisibility(View.INVISIBLE);
                _handler.sendEmptyMessage(0);
            }
        });
    }


    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
    }


    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_vision, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat currentFrame = inputFrame.rgba();
        if (_recording) {
            _monitor.newCameraAverage((float) Core.mean(currentFrame).val[0]);
        }
        return currentFrame;
    }

    public void updateRate() {
        Pair<Double, Double> r = _monitor.getRate();
        TextView view = (TextView)findViewById(R.id.textView);

        String peaksVal = "...";
        String fftVal = "...";
        if (r.first > 0) {
            peaksVal = Integer.toString((int)Math.round(r.first));
        }
        if (r.second > 0) {
            fftVal = Integer.toString((int)Math.round(r.second));
        }
        view.setText("PK: " + peaksVal + "\nFFT: " + fftVal);

        _handler.sendEmptyMessageDelayed(0, 1000);
    }

    private class FFTHandler extends Handler {
        private VisionActivity _activity = null;

        public FFTHandler(VisionActivity activity) {
            _activity = activity;
        }

        @Override
        public void handleMessage(Message message) {
            _activity.updateRate();
        }
    }
}
