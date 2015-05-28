// CSE P 590: Ubiquitous Computing (Spring 2015)
// Assignment 5 - Microcontrollers and Sensors
// Jeff Weiner (jdweiner@cs.washington.edu)

#include <RFduinoBLE.h>

// Config
int pin = 2; // heart rate data line on GPIO 2
int waitMillis = 1000 / 15; // ~15fps, just like the Android A2 camera
const int rawValuesCount = 5*2+1;

// Globals
int lastTime = 0;
int lastMedian = -1;
boolean rawValuesFilled = false;
int rawValuesNext = 0;
int rawValues[rawValuesCount];

void setup() {
  // Setup RFduinoBLE
  RFduinoBLE.deviceName = "UWCSEP590-A5";
  RFduinoBLE.advertisementData = "jdw";
  RFduinoBLE.begin();
  
  // Setup serial
  Serial.begin(4800);
  
  // Setup pin
  pinMode(pin, INPUT);
}

void loop() {
  int time = millis();
  if ((lastTime + waitMillis) < time) {
    lastTime = time;
    
    int reading = analogRead(pin);
    
    // Add reading to raw data set
    rawValues[rawValuesNext] = reading;
    rawValuesNext++;
    if (rawValuesNext == rawValuesCount) {
      rawValuesFilled = true;
      rawValuesNext = 0;
    }
    
    if (rawValuesFilled) {
      // Pull out a median-filtered value!
      int median = getMedian();
      
      // Look for peaks
      if (lastMedian != -1 && lastMedian > median) {
        // Found a peak!  Tell everyone!
        Serial.print("Found a peak: ");
        Serial.println(time);
        //RFduinoBLE.send(time);
      }
      lastMedian = median;
    }
  }
  else {
    // We've not waited long enough yet, let's take a nap
    int waitFor = (lastTime + waitMillis) - time;
    RFduino_ULPDelay(waitFor);
  }
}

int getMedian() {
  int sorted[rawValuesCount];
  for (int i = 0; i < rawValuesCount; i++) {
    sorted[i] = rawValues[i];
  }

  // It's been so long since I wrote something like this >,<;;;
  // Bubble Sort: the sort algorithm of champions!!!
  for (int i = 0; i < rawValuesCount; i++) {
    boolean finishEarly = true;
    for (int j = 1; j < (rawValuesCount-i); j++) {
      if (sorted[j-1] > sorted[j]) {
        int swap = sorted[j];
        sorted[j] = sorted[j-1];
        sorted[j-1] = swap;
        finishEarly = false;
      }
    }
    if (finishEarly) {
      break;
    }
  }
  
  return sorted[rawValuesCount/2];
}
