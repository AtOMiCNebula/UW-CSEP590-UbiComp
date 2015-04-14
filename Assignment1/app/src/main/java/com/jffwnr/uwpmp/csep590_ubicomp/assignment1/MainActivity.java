package com.jffwnr.uwpmp.csep590_ubicomp.assignment1;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {
    private StepCounter _stepCounter;
    private TextView _textViewSteps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _stepCounter = new StepCounter(this);
        if (savedInstanceState != null && savedInstanceState.containsKey("steps")) {
            _stepCounter.setStepCount(savedInstanceState.getInt("steps"));
        }
        _textViewSteps = (TextView)findViewById(R.id.steps);

        onStepCountUpdate(_stepCounter.getStepCount());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        _stepCounter.onPause();
    }

    @Override
    public void onPause() {
        super.onPause();
        _stepCounter.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        _stepCounter.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        state.putInt("steps", _stepCounter.getStepCount());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    public void onStepCountUpdate(int stepCount) {
        _textViewSteps.setText("Step count: " + stepCount);
    }

    public void button_onClick(View view) {
        _stepCounter.setStepCount(0);
        onStepCountUpdate(0);
    }
}
