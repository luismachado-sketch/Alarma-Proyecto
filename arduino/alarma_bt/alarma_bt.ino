/*
 * Alarma IoT - Arduino UNO + HC-05 + PIR + 2 Relés
 * 
 * Conexiones:
 *   HC-05 TX  -> Pin 10 (SoftwareSerial RX)
 *   HC-05 RX  -> Pin 11 (SoftwareSerial TX)
 *   PIR OUT   -> Pin 2  (Interrupción)
 *   Relé 1    -> Pin 7  (Sirena)
 *   Relé 2    -> Pin 8  (Luz)
 * 
 * Protocolo Bluetooth:
 *   Arduino -> App: SENSOR:motion:1, STATUS:siren:1, etc.
 *   App -> Arduino: CMD:siren:1, CMD:arm:0, etc.
 */

#include <SoftwareSerial.h>

// === PINES ===
#define BT_RX       10    // HC-05 TX conectado a Arduino pin 10
#define BT_TX       11    // HC-05 RX conectado a Arduino pin 11
#define PIR_PIN     2     // Sensor PIR (interrupción INT0)
#define RELAY_1     7     // Relé 1 - Sirena
#define RELAY_2     8     // Relé 2 - Luz

// === ESTADO DEL SISTEMA ===
bool sistemaArmado = false;
bool sirenaEncendida = false;
bool luzEncendida = false;
bool movimientoDetectado = false;
bool estadoMovimientoAnterior = false;

// === BLUETOOTH ===
SoftwareSerial bluetooth(BT_RX, BT_TX);

// === TIMING ===
unsigned long ultimoEnvioMovimiento = 0;
const unsigned long INTERVALO_ENVIO = 500; // Enviar estado cada 500ms max
unsigned long ultimoParpadeo = 0;
bool estadoLed = false;

void setup() {
  // Serial para debug (monitor serial)
  Serial.begin(9600);
  
  // Bluetooth
  bluetooth.begin(9600);
  
  // Pines de salida
  pinMode(RELAY_1, OUTPUT);
  pinMode(RELAY_2, OUTPUT);
  
  // Pin de entrada
  pinMode(PIR_PIN, INPUT);
  
  // Estado inicial: todo apagado
  digitalWrite(RELAY_1, LOW);
  digitalWrite(RELAY_2, LOW);
  
  // Mensaje de inicio
  Serial.println(F("=== Alarma IoT Iniciada ==="));
  Serial.println(F("Sistema desarmado. Esperando comandos BT..."));
  
  // Enviar estado inicial al conectarse
  delay(1000);
  enviarEstado();
}

void loop() {
  // === LEER COMANDOS BLUETOOTH ===
  if (bluetooth.available()) {
    String comando = bluetooth.readStringUntil('\n');
    comando.trim();
    if (comando.length() > 0) {
      procesarComando(comando);
    }
  }
  
  // === LEER SENSOR PIR ===
  bool movimientoActual = digitalRead(PIR_PIN) == HIGH;
  
  // Detectar cambio de estado
  if (movimientoActual != estadoMovimientoAnterior) {
    estadoMovimientoAnterior = movimientoActual;
    
    if (movimientoActual) {
      // Movimiento detectado
      Serial.println(F("[PIR] Movimiento DETECTADO"));
      bluetooth.println(F("SENSOR:motion:1"));
      movimientoDetectado = true;
      
      // Si el sistema está armado, activar alarmas
      if (sistemaArmado) {
        activarSirena();
        activarLuz();
        bluetooth.println(F("ALERT:motion:1"));
        Serial.println(F("[ALERTA] Sistema armado - Alarmas activadas"));
      }
    } else {
      // Sin movimiento
      Serial.println(F("[PIR] Sin movimiento"));
      bluetooth.println(F("SENSOR:motion:0"));
      movimientoDetectado = false;
    }
  }
  
  // === ENVIAR ESTADO PERIÓDICAMENTE ===
  if (millis() - ultimoEnvioMovimiento > INTERVALO_ENVIO) {
    ultimoEnvioMovimiento = millis();
    enviarEstadoSensores();
  }
  
  delay(50); // Pequeña pausa para estabilidad
}

// === PROCESAR COMANDOS RECIBIDOS ===
void procesarComando(String cmd) {
  Serial.print(F("[BT] Comando recibido: "));
  Serial.println(cmd);
  
  // CMD:siren:1 o CMD:siren:0
  if (cmd.startsWith("CMD:siren:")) {
    int valor = cmd.charAt(10) - '0';
    if (valor == 1) {
      activarSirena();
    } else {
      apagarSirena();
    }
  }
  // CMD:light:1 o CMD:light:0
  else if (cmd.startsWith("CMD:light:")) {
    int valor = cmd.charAt(10) - '0';
    if (valor == 1) {
      activarLuz();
    } else {
      apagarLuz();
    }
  }
  // CMD:arm:1 o CMD:arm:0
  else if (cmd.startsWith("CMD:arm:")) {
    int valor = cmd.charAt(8) - '0';
    if (valor == 1) {
      sistemaArmado = true;
      Serial.println(F("[SISTEMA] ARMADO"));
      bluetooth.println(F("STATUS:armed:1"));
    } else {
      sistemaArmado = false;
      apagarSirena();
      apagarLuz();
      Serial.println(F("[SISTEMA] DESARMADO"));
      bluetooth.println(F("STATUS:armed:0"));
    }
  }
  // CMD:status
  else if (cmd == "CMD:status") {
    enviarEstado();
  }
  // Comando no reconocido
  else {
    Serial.println(F("[BT] Comando no reconocido"));
  }
}

// === CONTROL DE ALARMAS ===
void activarSirena() {
  digitalWrite(RELAY_1, HIGH);
  sirenaEncendida = true;
  Serial.println(F("[RELÉ 1] Sirena ON"));
  bluetooth.println(F("STATUS:siren:1"));
}

void apagarSirena() {
  digitalWrite(RELAY_1, LOW);
  sirenaEncendida = false;
  Serial.println(F("[RELÉ 1] Sirena OFF"));
  bluetooth.println(F("STATUS:siren:0"));
}

void activarLuz() {
  digitalWrite(RELAY_2, HIGH);
  luzEncendida = true;
  Serial.println(F("[RELÉ 2] Luz ON"));
  bluetooth.println(F("STATUS:light:1"));
}

void apagarLuz() {
  digitalWrite(RELAY_2, LOW);
  luzEncendida = false;
  Serial.println(F("[RELÉ 2] Luz OFF"));
  bluetooth.println(F("STATUS:light:0"));
}

// === ENVIAR ESTADO COMPLETO ===
void enviarEstado() {
  bluetooth.println(F("STATUS:armed:") + String(sistemaArmado ? "1" : "0"));
  bluetooth.println(F("STATUS:siren:") + String(sirenaEncendida ? "1" : "0"));
  bluetooth.println(F("STATUS:light:") + String(luzEncendida ? "1" : "0"));
  bluetooth.println(F("SENSOR:motion:") + String(movimientoDetectado ? "1" : "0"));
}

// === ENVIAR ESTADO DE SENSORES ===
void enviarEstadoSensores() {
  // Solo enviar si hay cambio o periódicamente
  static bool ultimoEstadoArmed = false;
  static bool ultimoEstadoSiren = false;
  static bool ultimoEstadoLight = false;
  
  if (sistemaArmado != ultimoEstadoArmed) {
    ultimoEstadoArmed = sistemaArmado;
    bluetooth.println(F("STATUS:armed:") + String(sistemaArmado ? "1" : "0"));
  }
  if (sirenaEncendida != ultimoEstadoSiren) {
    ultimoEstadoSiren = sirenaEncendida;
    bluetooth.println(F("STATUS:siren:") + String(sirenaEncendida ? "1" : "0"));
  }
  if (luzEncendida != ultimoEstadoLight) {
    ultimoEstadoLight = luzEncendida;
    bluetooth.println(F("STATUS:light:") + String(luzEncendida ? "1" : "0"));
  }
}
