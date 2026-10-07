# Verificación de Firestore real

Fecha: 6 de octubre de 2026. Proyecto: cloudsync-datalab. Base: (default). Colección: registros, sin espacios finales.

La cuenta de servicio suministrada autenticó el backend Java. Las operaciones siguientes se ejecutaron mediante la API del proyecto y el SDK de Firestore, sin emulador.

| Comprobación | Resultado confirmado |
| --- | --- |
| GET /api/registros?limit=1, antes de escribir | HTTP 200; colección sin documentos |
| GET del ID BX2oGqeu7pVD4g7OsVVs indicado inicialmente | HTTP 404; ese documento no está en registros |
| Importar examples/prueba-firebase.csv | HTTP 200; read=2, created=2, skipped=0, failed=0 |
| Leer u4-demo-ana-20261006 | HTTP 200; diez campos de datos como texto |
| Leer u4-demo-luis-20261006 | HTTP 200; diez campos de datos como texto |
| Crear un documento temporal ficticio | HTTP 201 |
| Consultar ese documento temporal | HTTP 200 |
| Editar la dirección y volver a consultar | HTTP 200; nuevo valor confirmado |
| Eliminar ese mismo documento temporal | HTTP 204 |
| Consultarlo después de eliminar | HTTP 404 |
| Reimportar el mismo CSV | HTTP 200; read=2, created=0, skipped=2, failed=0 |
| Abrir la interfaz en Safari | Lista con Ana y Luis; conexión consultada desde el backend |

Quedaron guardados dos documentos de demostración: u4-demo-ana-20261006 y u4-demo-luis-20261006. Sus nombres, correos, direcciones, teléfonos y universidad son ficticios; los correos usan example.invalid. El documento temporal se eliminó después de la prueba. Todos los valores de los ejemplos son strings, incluidos Edad, Semestre y Telefono. Los ceros iniciales se conservaron.

El JSON privado permanece fuera del proyecto. backend/config/local.properties solo guarda su ruta y está ignorado por Git. El ZIP distribuye local.example.properties y no incluye local.properties ni la clave.

La conexión real y las cuatro operaciones están verificadas. Para la entrega académica siguen haciendo falta la bitácora PDF con sus evidencias, autor y referencias, y el acceso del tutor.
