/*
 * Puente simple BLE<->UART para diagnostico.
 * HC-06(BLE) TX -> D10, RX -> D11. USB serial a 9600.
 */
#include <SoftwareSerial.h>

SoftwareSerial bt(10, 11);

void setup() {
  Serial.begin(9600);
  bt.begin(9600);
  delay(300);
  Serial.println(F("BRIDGE_SIMPLE listo"));
}

void loop() {
  if (Serial.available()) bt.write(Serial.read());
  if (bt.available()) Serial.write(bt.read());
}
