package co.edu.poligran.registros.service;

import co.edu.poligran.registros.dto.Schema;
import co.edu.poligran.registros.error.ApiException;
import java.time.LocalDate;
import java.util.*;

/** La conversión depende del esquema confirmado, nunca de la apariencia del dato. */
public class RegistroValidator {
    private final Schema schema;
    public RegistroValidator(Schema schema) { this.schema = schema; }
    public Schema schema() { return schema; }
    public String id(Map<String, Object> data) { return (String) data.get(schema.idField()); }
    public String validateId(String id) {
        if (id == null || id.isBlank() || id.length() > 128 || id.chars().anyMatch(c -> c == '/' || c == '\\' || Character.isISOControl(c)) || id.equals(".") || id.equals("..") || id.matches("__.*__"))
            throw ApiException.invalid("Identificador inválido: usa de 1 a 128 caracteres, sin barras ni caracteres de control.");
        return id;
    }
    public Map<String, Object> validate(Map<String, ?> input) {
        if (schema.idField() == null) throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "Falta confirmar el esquema del CSV y la configuración de Firebase.");
        if (input == null) throw ApiException.invalid("El registro es obligatorio.");
        if (schema.generatedId()) {
            var normalized = new LinkedHashMap<String, Object>();
            input.forEach((key, value) -> {
                String name = key.strip();
                if (normalized.containsKey(name)) throw ApiException.invalid("Hay campos duplicados al quitar espacios: " + name);
                normalized.put(name, value);
            });
            input = normalized;
        }
        var known = schema.fields().stream().map(Schema.Field::name).toList();
        if (input.keySet().stream().anyMatch(key -> !known.contains(key) && !(schema.generatedId() && key.equals(schema.idField()))))
            throw ApiException.invalid("Hay campos desconocidos en el registro.");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var field : schema.fields()) {
            Object value = input.get(field.name());
            if (value == null || value instanceof String s && s.isBlank()) {
                if (field.required() || field.name().equals(schema.idField()))
                    throw ApiException.invalid("El campo '" + field.name() + "' es obligatorio.");
                result.put(field.name(), schema.generatedId() && field.type() == Schema.Type.TEXT ? "" : null);
                continue;
            }
            try {
                Object converted = switch (field.type()) {
                    case TEXT -> {
                        if (!(value instanceof String s) || s.length() > 10000) throw new IllegalArgumentException();
                        yield s;
                    }
                    case INTEGER -> {
                        String s = value.toString().strip();
                        if (!s.matches("-?[0-9]+")) throw new IllegalArgumentException();
                        long n = Long.parseLong(s);
                        if (n < -9007199254740991L || n > 9007199254740991L) throw new IllegalArgumentException();
                        yield n;
                    }
                    case DECIMAL -> {
                        String s = value.toString();
                        if (!s.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) throw new IllegalArgumentException();
                        double n = Double.parseDouble(s);
                        if (!Double.isFinite(n)) throw new IllegalArgumentException();
                        yield n;
                    }
                    case BOOLEAN -> {
                        if (!value.equals(true) && !value.equals(false) && !value.equals("true") && !value.equals("false"))
                            throw new IllegalArgumentException();
                        yield Boolean.parseBoolean(value.toString());
                    }
                    case DATE -> LocalDate.parse(value.toString()).toString();
                };
                result.put(field.name(), converted);
            } catch (RuntimeException e) {
                throw ApiException.invalid("El campo '" + field.name() + "' requiere tipo " + field.type() + ".");
            }
        }
        if (schema.generatedId()) {
            Object suppliedId = input.get(schema.idField());
            if (suppliedId != null && !suppliedId.equals("")) {
                if (!(suppliedId instanceof String)) throw ApiException.invalid("El ID del documento debe ser texto.");
                result.put(schema.idField(), validateId((String) suppliedId));
            }
        } else validateId(id(result));
        return result;
    }
}
