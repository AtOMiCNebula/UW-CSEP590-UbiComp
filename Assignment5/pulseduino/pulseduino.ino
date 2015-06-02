// CSE P 590: Ubiquitous Computing (Spring 2015)
// Assignment 5 - Microcontrollers and Sensors
// Jeff Weiner (jdweiner@cs.washington.edu)

#include <RFduinoBLE.h>

// Config
const int pin = 2; // heart rate data line on GPIO 2
const int waitSensor = 1000 / 15; // ~15fps for sensor readings, just like the Android A2 camera
const int waitBTLE = 1000 / 2; // ~2fps for message delivery
const int waitBeat = 1000 / 5; // wait 200ms between beats (hope nobody goes above 300bpm, lol)
const int rawValuesCount = 7;
const int beatsCount = 5; // number of beats to send per packet (max 20 bytes?)

// Globals
int lastSensor = 0;
int lastBTLE = 0;
int lastMedian1 = -1;
int lastMedian2 = -1;
int lastBeat = -1;
boolean rawValuesFilled = false;
int rawValuesNext = 0;
int rawValues[rawValuesCount];
int beats[beatsCount];

void setup() {
  // Initialize arrays
  for (int i = 0; i < rawValuesCount; i++) {
    rawValues[i] = 0;
  }
  for (int i = 0; i < beatsCount; i++) {
    beats[i] = 0;
  }
  
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
  
  // Is it time for a new sensor reading?
  if ((lastSensor + waitSensor) < time) {
    lastSensor = time;
    
    int reading = analogRead(pin);
    Serial.print(time);
    Serial.print(": reading ");
    Serial.println(reading);
    
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
      if (((lastBeat + waitBeat) < time) && lastMedian2 != -1 &&
            median < lastMedian1 && !(lastMedian1 < lastMedian2)) {
        lastBeat = time;
        
        // Found a peak!  Update the list!
        for (int i = 1; i < beatsCount; i++) {
          beats[i-1] = beats[i];
        }
        beats[beatsCount-1] = time;
        
        Serial.print(time);
        Serial.print(": BEAT     median=");
        Serial.print(median);
        Serial.print(", lastMedian1=");
        Serial.print(lastMedian1);
        Serial.print(", lastMedian2=");
        Serial.println(lastMedian2);
      }
      lastMedian2 = lastMedian1;
      lastMedian1 = median;
    }
  }
  
  // Is it time to deliver a new packet?
  if ((lastBTLE + waitBTLE) < time) {
    lastBTLE = time;
    
    RFduinoBLE.send((char*)beats, sizeof(beats));
  }
  
  // Done for now, wait until we have more work to do
  int waitFor = min((lastSensor + waitSensor), (lastBTLE + waitBTLE)) - millis();
  if (waitFor > 0) {
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
