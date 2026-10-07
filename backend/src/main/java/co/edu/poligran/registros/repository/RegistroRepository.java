package co.edu.poligran.registros.repository;

import co.edu.poligran.registros.dto.PageResult;
import java.util.Map;

/** El adaptador Firebase debe realizar creación condicional y paginación en el servidor. */
public interface RegistroRepository {
    default String newId() { return java.util.UUID.randomUUID().toString(); }
    PageResult list(int limit, String cursor);
    Map<String, Object> get(String id);
    void create(String id, Map<String, Object> data);
    void update(String id, Map<String, Object> data);
    void delete(String id);
}
