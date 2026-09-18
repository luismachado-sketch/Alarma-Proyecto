# Diagrama de Conexiones - Circuito Alarma IoT

## Vista superior del Protoboard

```
                    PROTOBOARD
    ┌──────────────────────────────────────────┐
    │  + ─────────────────────────────────── + │  ← Riel de power (+5V)
    │  - ─────────────────────────────────── - │  ← Riel de ground (GND)
    │                                          │
    │  a b c d e    f g h i j    k l m n o    │
    │  . . . . .    . . . . .    . . . . .    │
    │  . . . . .    . . . . .    . . . . .    │
    │  . . . . .    . . . . .    . . . . .    │
    │  . . . . .    . . . . .    . . . . .    │
    │  . . . . .    . . . . .    . . . . .    │
    │  . . . . .    . . . . .    . . . . .    │
    │  . . . . .    . . . . .    . . . . .    │
    │  . . . . .    . . . . .    . . . . .    │
    │  . . . . .    . . . . .    . . . . .    │
    │  . . . . .    . . . . .    . . . . .    │
    │  + ─────────────────────────────────── + │
    │  - ─────────────────────────────────── - │
    └──────────────────────────────────────────┘
```

## Conexiones detalladas

### 1. Arduino UNO → Protoboard (Power)

```
Arduino 5V  ──────────────→  Riel + (rojo) del protoboard
Arduino GND ──────────────→  Riel - (negro) del protoboard
```

### 2. HC-05 Bluetooth

```
HC-05 Pin    →  Protoboard       →  Arduino
─────────────────────────────────────────────
VCC          →  Riel +           →  (via protoboard 5V)
GND          →  Riel -           →  (via protoboard GND)
TX           →  columna f, fila 1 →  Pin 10 (Arduino)
RX           →  columna g, fila 1 →  Pin 11 (Arduino)
```

**Nota importante**: El HC-05 TX va a Arduino pin 10 (RX software), y el HC-05 RX va a Arduino pin 11 (TX software). NO conectar RX del HC-05 directamente al pin 11 sin un divisor de voltaje si hay problemas, pero en la mayoría de casos funciona directo.

### 3. Sensor PIR

```
PIR Pin      →  Protoboard       →  Arduino
─────────────────────────────────────────────
VCC (rojo)   →  Riel +           →  (via protoboard 5V)
GND (negro)  →  Riel -           →  (via protoboard GND)
OUT (blanco) →  columna h, fila 5 →  Pin 2 (Arduino)
```

### 4. Módulo Relé 2 Canales

```
Relé Pin     →  Protoboard       →  Arduino
─────────────────────────────────────────────
VCC          →  Riel +           →  (via protoboard 5V)
GND          →  Riel -           →  (via protoboard GND)
IN1          →  columna k, fila 8 →  Pin 7 (Arduino)
IN2          →  columna l, fila 8 →  Pin 8 (Arduino)
```

### 5. Sirena/Bocina (conectada al Relé 1)

```
Sirena cable rojo  →  Terminal COM del Relé 1
Sirena cable negro →  Terminal NO del Relé 1
Fuente de poder    →  Terminal COM del Relé 1
GND común          →  Terminal NO del Relé 1
```

**Importante**: La sirena necesita su propia fuente de poder (batería o adaptador) ya que el Arduino NO puede alimentarla directamente. El relé actúa como interruptor.

### 6. Luz Roja/Domo (conectada al Relé 2)

```
Luz cable rojo   →  Terminal COM del Relé 2
Luz cable negro  →  Terminal NO del Relé 2
Fuente de poder  →  Terminal COM del Relé 2
GND común        →  Terminal NO del Relé 2
```

**Mismo caso**: La luz necesita su propia fuente de poder.

## Diagrama de cableado simplificado

```
                    ┌─────────────┐
                    │  ARDUINO    │
                    │    UNO      │
                    │             │
    ┌───────────────┤ D10 (RX)    │◄──── HC-05 TX
    │───────────────┤ D11 (TX)    │────► HC-05 RX
    │               │             │
    │───────────────┤ D2          │◄──── PIR OUT
    │               │             │
    │───────────────┤ D7          │────► Relé IN1 (Sirena)
    │───────────────┤ D8          │────► Relé IN2 (Luz)
    │               │             │
    │───────────────┤ 5V          │────► Power Bus (+)
    │───────────────┤ GND         │────► Power Bus (-)
    │               └─────────────┘
    │
    │    ┌──────────┐
    │    │  HC-05   │
    │    │          │
    └────┤ TX  RX   │
         │ VCC GND  │
         └──────────┘
              │
              ▼
    ┌──────────────┐     ┌──────────┐
    │  SENSOR PIR  │     │  RELÉ 2  │
    │              │     │  CANALES │
    │  OUT → D2    │     │          │
    │  VCC → 5V    │     │ IN1 → D7 │──→ SIRENA
    │  GND → GND   │     │ IN2 → D8 │──→ LUZ
    └──────────────┘     │ VCC → 5V │
                         │ GND → GND│
                         └──────────┘
```

## Pasos de ensamblaje

1. **Colocar Arduino UNO** en el extremo del protoboard
2. **Conectar power**: Arduino 5V → riel +, Arduino GND → riel -
3. **Colocar HC-05** en el protoboard, conectar VCC/GND al riel, TX→D10, RX→D11
4. **Colocar PIR** en el protoboard, conectar VCC/GND al riel, OUT→D2
5. **Colocar Relé** en el protoboard, conectar VCC/GND al riel, IN1→D7, IN2→D8
6. **Conectar Sirena** a los terminales del Relé 1 (COM y NO)
7. **Conectar Luz** a los terminales del Relé 2 (COM y NO)
8. **Conectar fuentes de poder** externas para sirena y luz (si es necesario)
9. **Verificar** todas las conexiones antes de energizar
10. **Subir el código** al Arduino desde Arduino IDE

## Advertencias

- **NO conectar la sirena/luz directamente al Arduino** - usar los relés como interruptores
- **Verificar polaridad** del HC-05 (VCC, GND, TX, RX) antes de conectar
- El PIR tiene un tiempo de calibración de ~1 minuto después de encender
- Si el HC-05 no responde, verificar la velocidad baud (9600 por defecto)
- Usar cables de colores: rojo=5V, negro=GND, otros=señal
