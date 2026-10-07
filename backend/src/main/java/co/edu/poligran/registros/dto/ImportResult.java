package co.edu.poligran.registros.dto;

import java.util.List;

public record ImportResult(int read, int created, int skipped, int failed, List<RowError> errors) {
    public record RowError(long row, String message) {}
}
