# Pendientes tecnicos

## HC-06 "ALARMA" (modulo BLE tipo HM-10) — PENDIENTE

- Identificado por escaneo como **BLE**, no clasico:
  - Servicio `0000FFE0`, caracteristica `0000FFE1` (notify + write).
  - MAC `25:FB:A3:A6:E9:D2`.
- La app conecta por BLE (`onConnectionStateChange status=0 newState=2`), pero el modulo
  **corta la conexion** a los pocos segundos (`status=19`, peer terminated) y pide un
  emparejamiento BLE con PIN (probablemente `AT+TYPE2/3`). No usa PIN por defecto.
- El **UART modulo<->Arduino no entrega datos** en ninguna direccion:
  - BLE -> Arduino: en D10 solo se lee ruido (`\xff`).
  - Arduino -> modulo: no llega ninguna notificacion BLE.
- Hipotesis: RX del modulo a 5V (deberia ser 3.3V), mal contacto, o UART danado.

### Proximos pasos HC-06
1. Prueba de loopback (puente D10-D11) para validar el lado Arduino.
2. Revisar divisor de voltaje en D11 -> RX del modulo (3.3V).
3. Si el UART responde por AT: `AT+RENEW` (factory reset) y `AT+PIN1234`.
4. Si nunca responde con loopback OK: UART del modulo danado -> reemplazar.

## HC-05 (clasico SPP) — EN CURSO

- Cableado: VCC -> 5V, GND -> GND, TX -> D10, RX -> D11 (divisor 3.3V recomendado),
  **EN/KEY sin conectar** (para que arranque en modo datos, no en AT).
- PIN por defecto: `1234` (o `0000`). Baud 9600.
- Firmware a subir: `arduino/alarma_bt/alarma_bt.ino`.
- La app ya soporta clasico (SPP) y BLE de forma automatica segun el tipo de dispositivo.

## Nota de arquitectura

La app soporta dos transportes:
- **Clasico SPP (RFCOMM)** para HC-05.
- **BLE GATT (FFE0/FFE1)** para modulos seriales tipo HM-10.

El protocolo con el Arduino no cambia (`CMD:*` / `STATUS:*` / `SENSOR:*` / `ALERT:*`).
