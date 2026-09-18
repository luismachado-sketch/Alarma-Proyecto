# Plan de Implementacion - Fase Hardware y App IoT

## Arquitectura del Sistema

```
┌─────────────────┐        Bluetooth (HC-05)        ┌─────────────────┐
│   APP ANDROID   │ ◄══════════════════════════════► │    ARDUINO UNO   │
│   (tu celular)  │                                  │                  │
│                 │   Datos: sensor, estado, comandos │  ┌────────────┐ │
│  ┌───────────┐  │                                  │  │  PIR       │ │
│  │ Dashboard │  │   ← Estado del sensor/movimiento  │  │  (GPIO 2)  │ │
│  │  Screen   │  │                                  │  └────────────┘ │
│  └───────────┘  │                                  │                  │
│  ┌───────────┐  │   → Comando: activar/desactivar  │  ┌────────────┐ │
│  │  Control  │  │                                  │  │  Relé 1    │ │
│  │  Screen   │  │                                  │  │  (Sirena)  │ │
│  └───────────┘  │                                  │  │  GPIO 7    │ │
│  ┌───────────┐  │                                  │  └────────────┘ │
│  │Notificac. │  │   ← Alerta de movimiento         │                  │
│  │  push     │  │                                  │  ┌────────────┐ │
│  └───────────┘  │                                  │  │  Relé 2    │ │
└─────────────────┘                                  │  │  (Luz)     │ │
                                                     │  │  GPIO 8    │ │
                                                     │  └────────────┘ │
                                                     └─────────────────┘
```

## Componentes

| # | Componente | Funcion |
|---|-----------|---------|
| 1 | Arduino UNO | Placa principal - controla todo |
| 2 | Protoboard | Base para conexiones temporales |
| 3 | HC-05 Bluetooth | Comunicacion inalambrica con la app Android |
| 4 | Modulo Relé 2 canales | Encender/apagar sirena y luz externa |
| 5 | Sensor PIR (movimiento) | Detecta presencia/movimiento |
| 6 | Sirena/Bocina | Alerta sonora de alarma |
| 7 | Luz roja (domo) | Alerta visual de alarma |

## Conexiones del Circuito

| Componente | Pin Arduino | Pin Componente | Notas |
|-----------|-------------|----------------|-------|
| HC-05 TX | D10 (RX software) | TX | HC-05 transmite -> Arduino recibe |
| HC-05 RX | D11 (TX software) | RX | Arduino transmite -> HC-05 recibe |
| HC-05 VCC | 5V | VCC | |
| HC-05 GND | GND | GND | |
| PIR OUT | D2 (interrupcion) | OUT | Deteccion de movimiento |
| PIR VCC | 5V | VCC | |
| PIR GND | GND | GND | |
| Relé 1 (Sirena) | D7 | IN1 | Control sirena |
| Relé 2 (Luz) | D8 | IN2 | Control luz roja |
| Relé VCC | 5V | VCC | |
| Relé GND | GND | GND | |

## Protocolo de Comunicacion (Bluetooth)

### Mensajes Arduino -> App
```
SENSOR:motion:1        (movimiento detectado)
SENSOR:motion:0        (sin movimiento)
STATUS:siren:1         (sirena encendida)
STATUS:siren:0         (sirena apagada)
STATUS:light:1         (luz encendida)
STATUS:light:0         (luz apagada)
STATUS:armed:1         (sistema armado)
STATUS:armed:0         (sistema desarmado)
```

### Mensajes App -> Arduino
```
CMD:siren:1            (encender sirena)
CMD:siren:0            (apagar sirena)
CMD:light:1            (encender luz)
CMD:light:0            (apagar luz)
CMD:arm:1              (activar alarma)
CMD:arm:0              (desactivar alarma)
CMD:status             (solicitar estado)
```

## Funcionalidad de la App

### Dashboard - Datos en tiempo real
| Dato | Fuente | Descripcion |
|------|--------|-------------|
| Conexion BT | HC-05 | Conectado/Desconectado |
| Movimiento | PIR sensor | SI/NO + timestamp |
| Estado sirena | Relé 1 | Encendida/Apagada |
| Estado luz | Relé 2 | Encendida/Apagada |
| Sistema armado | Logica Arduino | Armado/Desarmado |

### Control - Acciones
| Accion | Comando BT | Efecto |
|--------|------------|--------|
| Activar alarma | CMD:arm:1 | PIR activa sirena+luz al detectar |
| Desactivar alarma | CMD:arm:0 | PIR ignora movimiento |
| Encender sirena | CMD:siren:1 | Relé 1 ON manual |
| Apagar sirena | CMD:siren:0 | Relé 1 OFF manual |
| Encender luz | CMD:light:1 | Relé 2 ON manual |
| Apagar luz | CMD:light:0 | Relé 2 OFF manual |

## Fases de Implementacion

### Fase 1: Hardware (Arduino)
1. Armar el circuito en el protoboard
2. Programar el Arduino con el codigo de control
3. Probar comunicacion serial con el HC-05

### Fase 2: App Android
1. Agregar permisos Bluetooth al manifest
2. Crear bluetooth/BluetoothManager.kt - conexion y comunicacion
3. Modificar DashboardScreen - mostrar datos del sensor en tiempo real
4. Modificar ControlScreen - switches que envian comandos BT
5. Agregar notificaciones push cuando se detecte movimiento

### Fase 3: Integracion
1. Emparejar HC-05 con el celular
2. Probar flujo completo: movimiento -> Arduino -> BT -> App -> notificacion
