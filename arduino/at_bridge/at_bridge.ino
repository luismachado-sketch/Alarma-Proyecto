/*
 * AT Bridge - Arduino UNO <-> modulo Bluetooth (HC-05 / HC-06)
 *
 * Al arrancar:
 *   Fase A: prueba SoftwareSerial(10,11) barriendo baudios enviando "AT".
 *   Fase B: si A falla, prueba SoftwareSerial(11,10) (cables invertidos).
 * Luego queda en modo puente transparente con la combinacion que respondio.
 *
 * Monitor serial: 9600 baudios, "Both NL & CR" (o sin ajuste de linea).
 */

#include <SoftwareSerial.h>

SoftwareSerial btA(10, 11);  // RX=D10, TX=D11
SoftwareSerial btB(11, 10);  // RX=D11, TX=D10  (por si estan invertidos)

const long CANDIDATOS[] = { 9600, 38400, 115200, 57600, 19200, 4800, 2400, 1200 };
const int NUM_BAUDIOS = sizeof(CANDIDATOS) / sizeof(CANDIDATOS[0]);

long baudDetectado = 0;
bool pinesInvertidos = false;

int probar(SoftwareSerial &bt, long b) {
  bt.begin(b);
  delay(150);
  while (bt.available()) bt.read();
  bt.print(F("AT"));
  delay(500);

  int recibidos = 0;
  unsigned long t0 = millis();
  while (millis() - t0 < 800) {
    while (bt.available()) {
      char c = (char)bt.read();
      Serial.write(c);
      recibidos++;
    }
  }
  bt.end();
  return recibidos;
}

long barrer(bool invertidos) {
  for (int i = 0; i < NUM_BAUDIOS; i++) {
    long b = CANDIDATOS[i];
    Serial.print(invertidos ? F("[inv] baud ") : F("baud "));
    Serial.print(b);
    Serial.print(F(" -> "));

    int n = invertidos ? probar(btB, b) : probar(btA, b);
    if (n == 0) {
      Serial.println(F("(sin respuesta)"));
    } else {
      Serial.println();
      Serial.print(F("[DETECTADO] baud="));
      Serial.print(b);
      Serial.println(invertidos ? F(" pines=INVERTIDOS (11,10)") : F(" pines=normales (10,11)"));
      pinesInvertidos = invertidos;
      return b;
    }
  }
  return 0;
}

void setup() {
  Serial.begin(9600);
  delay(400);
  Serial.println(F("=== AT BRIDGE: barrido de baudios ==="));
  Serial.println(F("Modulo encendido y SIN conectar (LED parpadeando)."));

  baudDetectado = barrer(false);
  if (baudDetectado == 0) {
    baudDetectado = barrer(true);
  }

  if (baudDetectado == 0) {
    Serial.println(F("[!] Ninguna combinacion respondio. Posibles causas:"));
    Serial.println(F("    - modulo BLE (no UART clasico)"));
    Serial.println(F("    - TX/RX no conectados o modulo sin 3.3V"));
    Serial.println(F("    - UART del modulo danado"));
    baudDetectado = 9600;
  }

  if (pinesInvertidos) {
    btB.begin(baudDetectado);
  } else {
    btA.begin(baudDetectado);
  }
  delay(100);

  Serial.print(F("=== Puente activo a "));
  Serial.print(baudDetectado);
  Serial.println(pinesInvertidos ? F(" (pines 11,10). Escribe AT. ===") : F(" (pines 10,11). Escribe AT. ==="));
}

void loop() {
  if (pinesInvertidos) {
    if (Serial.available()) btB.write(Serial.read());
    if (btB.available()) Serial.write(btB.read());
  } else {
    if (Serial.available()) btA.write(Serial.read());
    if (btA.available()) Serial.write(btA.read());
  }
}
