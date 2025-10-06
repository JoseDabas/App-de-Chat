# Guía de Notificaciones FCM - App Cerrada

## Problema Resuelto
Las notificaciones no llegaban cuando la aplicación estaba completamente cerrada (100% cerrada), solo funcionaban cuando estaba en segundo plano.

## Solución Implementada

### 1. Permisos Agregados en AndroidManifest.xml
```xml
<!-- Permisos para notificaciones en background -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

### 2. Configuración del Servicio FCM
- Agregado `android:directBootAware="true"` al servicio FCM
- Creado `BootReceiver` para manejar reinicios del dispositivo
- Canal de notificaciones configurado con máxima importancia

### 3. Estructura de Mensajes FCM

#### Para App Cerrada (RECOMENDADO)
Usar **SOLO datos** (sin campo `notification`):
```json
{
  "to": "TOKEN_FCM_DEL_USUARIO",
  "data": {
    "title": "Nombre del Remitente",
    "body": "Texto del mensaje",
    "senderName": "Nombre del Remitente",
    "messageText": "Texto del mensaje",
    "chatId": "ID_DEL_CHAT"
  }
}
```

#### Para App en Background
Usar `notification` + `data`:
```json
{
  "to": "TOKEN_FCM_DEL_USUARIO",
  "notification": {
    "title": "Nombre del Remitente",
    "body": "Texto del mensaje"
  },
  "data": {
    "senderName": "Nombre del Remitente",
    "messageText": "Texto del mensaje",
    "chatId": "ID_DEL_CHAT"
  }
}
```

## Comportamiento por Estado de la App

| Estado de la App | Estructura FCM | Comportamiento |
|------------------|----------------|----------------|
| **Primer Plano** | Cualquiera | `onMessageReceived()` se ejecuta |
| **Background** | `notification` + `data` | Android maneja automáticamente + `onMessageReceived()` |
| **Cerrada** | **Solo `data`** | `onMessageReceived()` se ejecuta y crea notificación |

## Características Implementadas

### ChatFirebaseMessagingService
- ✅ Detección del estado de la app (primer plano/background/cerrada)
- ✅ Manejo diferenciado según el estado
- ✅ Notificaciones de alta prioridad
- ✅ Icono personalizado (talknow.png)
- ✅ Navegación directa al chat correspondiente

### BootReceiver
- ✅ Reinicialización automática después de reinicio del dispositivo
- ✅ Actualización del token FCM

### Canal de Notificaciones
- ✅ Importancia alta (IMPORTANCE_HIGH)
- ✅ Visible en pantalla de bloqueo
- ✅ Bypass del modo "No Molestar"
- ✅ Luces LED y vibración habilitadas

## Pruebas Recomendadas

1. **App en Primer Plano**: Enviar mensaje FCM y verificar que aparezca la notificación
2. **App en Background**: Minimizar la app y enviar mensaje FCM
3. **App Cerrada**: Cerrar completamente la app (swipe up + cerrar) y enviar mensaje FCM
4. **Después de Reinicio**: Reiniciar el dispositivo y probar notificaciones

## Notas Importantes

- **Optimización de Batería**: En algunos dispositivos, puede ser necesario deshabilitar la optimización de batería para la app
- **Permisos de Notificación**: El usuario debe haber otorgado permisos de notificación
- **Estructura del Mensaje**: Para app cerrada, usar SOLO `data` (sin `notification`)
- **Token FCM**: Se guarda automáticamente en Firestore cuando el usuario inicia sesión

## Solución de Problemas

### Si las notificaciones no llegan cuando la app está cerrada:
1. Verificar que el mensaje FCM use SOLO `data` (sin `notification`)
2. Comprobar que los permisos de notificación estén otorgados
3. Verificar que la optimización de batería esté deshabilitada para la app
4. Revisar los logs de `ChatFirebaseMessagingService` para errores

### Logs Útiles:
```
TAG: FCMService
- "Mensaje recibido de: [sender]"
- "Estado de la app: [Primer plano/Background/Cerrada]"
- "Notificación mostrada: [title] - [body]"
```