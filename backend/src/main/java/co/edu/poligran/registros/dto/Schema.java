package co.edu.poligran.registros.dto;

import java.util.List;

public record Schema(String idField, List<Field> fields, boolean generatedId) {
    public Schema(String idField, List<Field> fields) { this(idField, fields, false); }
    public enum Type { TEXT, INTEGER, DECIMAL, BOOLEAN, DATE }
    public record Field(String name, Type type, boolean required) {}
}
