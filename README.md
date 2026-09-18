# Alarma-Proyecto

Aplicacion Android para monitoreo y control de dispositivos IoT, desarrollada para el curso TI3042 - Aplicaciones Moviles para IoT.

## Stack Tecnologico

| Componente | Tecnologia |
|------------|------------|
| IDE | Android Studio |
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material3 |
| Base de datos local | SQLite |
| Placa IoT | ESP32 (v1.2+) |
| Protocolo MQTT | Eclipse Paho (v1.2+) |

## Sistema de Autenticacion (v1.1)

La aplicacion implementa un sistema de autenticacion local utilizando **SQLite**.

### Metodos de autenticacion evaluados

| Opcion | Descripcion | Seleccion |
|--------|-------------|-----------|
| **SQLite** | Autenticacion local, sin internet | **SELECCIONADA** |
| Firebase Authentication | Autenticacion en la nube (Google/Email) | Pendiente v1.3 |
| API con MySQL | Backend externo | No seleccionada |

### Justificacion de la decision

- **Sin dependencia de servicios externos**: No requiere conexion a internet ni configuracion de servidores
- **Bajo consumo de recursos**: SQLite esta integrado nativamente en Android
- **Rapidez de implementacion**: Permite validar el flujo de autenticacion sin configurar Firebase ni backend
- **Seguridad basica**: Las contrasenas se almacenan con hash SHA-256, nunca en texto plano

### Estructura de la base de datos

```
Base de datos: alarma_users.db
Tabla: users
  - id: INTEGER PRIMARY KEY AUTOINCREMENT
  - email: TEXT UNIQUE NOT NULL
  - password: TEXT NOT NULL (hash SHA-256)
```

### Flujo de autenticacion

```
App inicia -> LoginScreen
  |-- Credenciales validas -> DashboardScreen
  |-- "Registrate" -> RegisterScreen
       |-- Registro exitoso -> LoginScreen
       |-- "Ya tengo cuenta" -> LoginScreen
```

### Seguridad

- **Hash SHA-256**: Las contrasenas se hashean antes de almacenarse
- **Normalizacion de email**: Se almacenan en minusculas y sin espacios
- **Validacion de entrada**: Campos obligatorios, formato email, longitud minima de 6 caracteres
- **Cierre de recursos**: La conexion a la base de datos se cierra despues de cada operacion

## Estructura del Proyecto

```
app/src/main/java/com/example/alarmaproyecto/
├── MainActivity.kt                    # Activity principal
├── data/
│   ├── UserDatabaseHelper.kt          # SQLite helper
│   └── UserRepository.kt              # Repositorio de datos
├── navigation/
│   └── AppNavigation.kt               # Navegacion entre pantallas
└── ui/
    ├── screens/
    │   ├── LoginScreen.kt             # Pantalla de login
    │   ├── RegisterScreen.kt          # Pantalla de registro
    │   └── DashboardScreen.kt         # Dashboard (placeholder)
    └── theme/
        ├── Color.kt                   # Colores IoT
        ├── Theme.kt                   # Tema Material3
        └── Type.kt                    # Tipografia
```

## Versiones

| Version | Fecha | Descripcion |
|---------|-------|-------------|
| v1.0 | Sep 2026 | Template inicial |
| v1.1 | Sep 2026 | Login con SQLite, navegacion, tema IoT |
| v1.2 | - | Conexion MQTT con ESP32 |
| v1.3 | - | Firebase Authentication |
| v1.4 | - | Almacenamiento y seguridad (TLS/SSL) |

## Como Ejecutar

1. Abrir el proyecto en Android Studio
2. Sincronizar Gradle: **File** > **Sync Project with Gradle Files**
3. Seleccionar dispositivo o emulador
4. Ejecutar: clic en boton **Run** (o `Shift + F10`)

## Criterios de Evaluacion (TI3042 Unidad 2)

| Criterio | Estado |
|----------|--------|
| 2.1.1 Herramientas de desarrollo movil | Completado |
| 2.1.2 Conexiones inalambricas | Pendiente (v1.2) |
| 2.1.3 Seguridad ISO 27400 | En progreso |
| 2.1.4 Interconexion entre dispositivos | Pendiente (v1.2) |
