# Guía de Pruebas - Notificaciones FCM Mejoradas

## Mejoras Implementadas

### 1. Configuraciones Anti-Optimización
- **Permisos agregados**: `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `DISABLE_KEYGUARD`, `SYSTEM_ALERT_WINDOW`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `FOREGROUND_SERVICE_DATA_SYNC`
- **Configuraciones de aplicación**: `persistent="true"`, `stopWithTask="false"`, `killAfterRestore="false"`
- **Servicio FCM de alta prioridad**: Prioridad 1000, `stopWithTask="false"`, `directBootAware="true"`

### 2. BackgroundService
- **Servicio en primer plano** que mantiene la app activa
- **Verificación periódica de tokens FCM** cada 30 minutos
- **Reinicio automático** después del reinicio del dispositivo
- **Persistencia de tokens** en SharedPreferences y Firestore

### 3. ChatFirebaseMessagingService Mejorado
- **Detección del estado de la app** (primer plano, fondo, cerrada)
- **Notificaciones de alta prioridad** para apps en fondo/cerradas
- **Manejo robusto de tokens** con reintentos automáticos
- **Notificaciones siempre visibles** independientemente del estado

## Instrucciones de Prueba

### Preparación
1. **Instalar la app actualizada** (ya instalada)
2. **Iniciar sesión** en la aplicación
3. **Verificar permisos** - La app solicitará permisos adicionales
4. **Observar notificación persistente** - "TalkNow activo" debe aparecer

### Prueba 1: App en Primer Plano
1. Mantener la app abierta y visible
2. Enviar mensaje FCM con estructura `data` únicamente:
```json
{
  "to": "TOKEN_FCM_DEL_USUARIO",
  "data": {
    "title": "Mensaje de prueba",
    "body": "App en primer plano",
    "senderId": "usuario_test",
    "senderName": "Usuario Test",
    "timestamp": "1234567890"
  }
}
```
3. **Resultado esperado**: Notificación personalizada + actualización en tiempo real

### Prueba 2: App en Segundo Plano
1. Abrir la app, luego presionar botón Home (no cerrar)
2. Enviar el mismo mensaje FCM
3. **Resultado esperado**: Notificación de alta prioridad visible inmediatamente

### Prueba 3: App Completamente Cerrada
1. Cerrar completamente la app (deslizar hacia arriba en recientes)
2. Verificar que la notificación "TalkNow activo" sigue visible
3. Enviar mensaje FCM
4. **Resultado esperado**: Notificación aparece inmediatamente

### Prueba 4: Después de Reinicio del Dispositivo
1. Reiniciar el dispositivo/emulador
2. NO abrir la app manualmente
3. Esperar 1-2 minutos para que BootReceiver inicie servicios
4. Enviar mensaje FCM
5. **Resultado esperado**: Notificación aparece sin necesidad de abrir la app

### Prueba 5: Verificación de Logs
Usar `adb logcat` para monitorear:
```bash
adb logcat | grep -E "(ChatFirebaseMessaging|BackgroundService|BootReceiver)"
```

**Logs esperados**:
- `BackgroundService creado`
- `Token FCM obtenido/actualizado`
- `Programación de verificación de token FCM iniciada`
- `Notificación de alta prioridad creada`

## Estructura de Mensaje FCM Requerida

### ✅ CORRECTO (Solo data)
```json
{
  "to": "TOKEN_FCM",
  "data": {
    "title": "Título del mensaje",
    "body": "Contenido del mensaje",
    "senderId": "id_del_remitente",
    "senderName": "Nombre del remitente",
    "timestamp": "timestamp_unix"
  }
}
```

### ❌ INCORRECTO (Con notification)
```json
{
  "to": "TOKEN_FCM",
  "notification": {
    "title": "Título",
    "body": "Contenido"
  },
  "data": { ... }
}
```

## Solución de Problemas

### Si las notificaciones no llegan:
1. **Verificar token FCM** en Firestore (colección `users`)
2. **Revisar logs** con `adb logcat`
3. **Verificar permisos** en Configuración > Apps > TalkNow
4. **Desactivar optimización de batería** manualmente si es necesario
5. **Verificar que BackgroundService esté activo** (notificación persistente visible)

### Si el servicio se detiene:
1. **Verificar configuración de batería** del dispositivo
2. **Revisar si la app está en lista blanca** de optimización
3. **Reiniciar la app** para reactivar servicios

## Características Implementadas

✅ Notificaciones cuando la app está cerrada  
✅ Servicio persistente en primer plano  
✅ Verificación automática de tokens FCM  
✅ Reinicio automático después del boot  
✅ Notificaciones de alta prioridad  
✅ Manejo robusto de errores  
✅ Persistencia de configuración  
✅ Prevención de optimizaciones del sistema  

## Notas Importantes

- El **BackgroundService** consume batería mínima pero mantiene la conectividad FCM
- Las **notificaciones de alta prioridad** pueden sonar incluso en modo silencioso
- El sistema puede seguir **optimizando la app** en dispositivos con gestión agresiva de batería
- Para **máxima efectividad**, recomendar a usuarios desactivar optimización de batería manualmente