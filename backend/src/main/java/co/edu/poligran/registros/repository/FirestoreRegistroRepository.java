package co.edu.poligran.registros.repository;

import co.edu.poligran.registros.dto.PageResult;
import co.edu.poligran.registros.error.ApiException;
import co.edu.poligran.registros.service.RegistroValidator;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Consultas limitadas; ninguna operación descarga la colección completa. */
public class FirestoreRegistroRepository implements RegistroRepository {
    private final Supplier<Firestore> client;
    private final String collectionName;
    private final RegistroValidator validator;
    public FirestoreRegistroRepository(Supplier<Firestore> client, String collectionName, RegistroValidator validator) {
        if (collectionName == null || collectionName.isBlank() || collectionName.contains("/"))
            throw new IllegalArgumentException("FIREBASE_COLLECTION debe ser el nombre exacto de una colección raíz.");
        this.client = client; this.collectionName = collectionName; this.validator = validator;
    }
    private CollectionReference collection() {
        try { return client.get().collection(collectionName); }
        catch (RuntimeException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo inicializar Firestore. Revisa app.firebase.credentials-file en config/local.properties o GOOGLE_APPLICATION_CREDENTIALS, los permisos del JSON y reinicia el backend.");
        }
    }
    public String newId() { return collection().document().getId(); }
    public PageResult list(int limit, String cursor) {
        if (limit < 1 || limit > 100) throw ApiException.invalid("El tamaño de página debe estar entre 1 y 100.");
        var collection = collection();
        Query query = collection.orderBy(FieldPath.documentId()).limit(limit + 1);
        if (cursor != null) query = query.startAfter(collection.document(validator.validateId(cursor)));
        var documents = await(query.get(), false).getDocuments();
        var items = documents.stream().limit(limit).map(this::decode).toList();
        return new PageResult(items, documents.size() > limit ? documents.get(limit - 1).getId() : null);
    }
    public Map<String, Object> get(String id) {
        var snapshot = await(collection().document(validator.validateId(id)).get(), false);
        if (!snapshot.exists()) throw ApiException.missing();
        return decode(snapshot);
    }
    public void create(String id, Map<String, Object> data) {
        try { await(collection().document(validator.validateId(id)).create(encode(data)), true); }
        catch (ApiException e) {
            if (e.status() == HttpStatus.SERVICE_UNAVAILABLE)
                throw new ApiException(e.status(), e.getMessage() + " ID del documento: " + id);
            throw e;
        }
    }
    public void update(String id, Map<String, Object> data) {
        var reference = collection().document(validator.validateId(id));
        var snapshot = await(reference.get(), false);
        if (!snapshot.exists()) throw ApiException.missing();
        // Conservar los nombres exactos de las claves existentes, incluso sus espacios.
        var existing = snapshot.getData();
        Map<String, Object> changes = new LinkedHashMap<>();
        for (var field : validator.schema().fields()) {
            String key = matchingKey(existing, field.name());
            changes.put(key == null ? field.name() : key, data.get(field.name()));
        }
        await(reference.update(changes, Precondition.updatedAt(snapshot.getUpdateTime())), true);
    }
    public void delete(String id) {
        var reference = collection().document(validator.validateId(id));
        var snapshot = await(reference.get(), false);
        if (!snapshot.exists()) throw ApiException.missing();
        await(reference.delete(Precondition.updatedAt(snapshot.getUpdateTime())), true);
    }
    private Map<String, Object> encode(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var field : validator.schema().fields()) result.put(field.name(), data.get(field.name()));
        return result;
    }
    private Map<String, Object> decode(DocumentSnapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        var data = snapshot.getData();
        result.put(validator.schema().idField(), snapshot.getId());
        for (var field : validator.schema().fields()) {
            String key = matchingKey(data, field.name());
            result.put(field.name(), key == null ? "" : data.get(key));
        }
        return result;
    }
    private String matchingKey(Map<String, Object> data, String name) {
        var keys = data.keySet().stream().filter(key -> key.strip().equals(name)).toList();
        if (keys.size() > 1) throw new ApiException(HttpStatus.CONFLICT,
            "El documento tiene claves ambiguas para '" + name + "'. Revisa los espacios en Firestore antes de editar.");
        return keys.isEmpty() ? null : keys.getFirst();
    }
    private <T> T await(ApiFuture<T> future, boolean write) {
        try { return future.get(15, TimeUnit.SECONDS); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw unavailable(write);
        } catch (TimeoutException e) {
            future.cancel(true); throw unavailable(write);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause instanceof ApiException api) throw api;
                if (cause instanceof com.google.api.gax.rpc.ApiException api) {
                    switch (api.getStatusCode().getCode()) {
                        case ALREADY_EXISTS: throw ApiException.duplicate();
                        case NOT_FOUND: throw ApiException.missing();
                        case PERMISSION_DENIED, UNAUTHENTICATED:
                            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Firestore rechazó el acceso. Revisa las credenciales y los permisos de la cuenta de servicio en cloudsync-datalab.");
                        case FAILED_PRECONDITION, ABORTED:
                            throw new ApiException(HttpStatus.CONFLICT,
                                "No se confirmó la operación: el documento pudo cambiar. Actualiza la lista y revisa el registro antes de reintentar.");
                        case INVALID_ARGUMENT: throw ApiException.invalid("Firestore rechazó los datos de la solicitud.");
                        default: throw unavailable(write);
                    }
                }
                cause = cause.getCause();
            }
            throw unavailable(write);
        }
    }
    private ApiException unavailable(boolean write) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, write
            ? "No se confirmó la escritura en Firestore. Consulta el ID del registro antes de reintentar."
            : "No se pudo consultar Firestore. Revisa tu conexión, credenciales y configuración del proyecto.");
    }
}
