package co.edu.poligran.registros.service;

import co.edu.poligran.registros.dto.ImportResult;
import co.edu.poligran.registros.dto.ImportResult.RowError;
import co.edu.poligran.registros.error.ApiException;
import co.edu.poligran.registros.repository.RegistroRepository;
import org.apache.commons.csv.*;
import org.springframework.http.HttpStatus;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CsvImportService {
    private final RegistroValidator validator;
    private final RegistroRepository repository;
    private final int maxRows;
    public CsvImportService(RegistroValidator validator, RegistroRepository repository, int maxRows) {
        this.validator = validator; this.repository = repository; this.maxRows = maxRows;
    }
    private record Pending(long row, Map<String, Object> data) {}
    public ImportResult importCsv(InputStream stream, String delimiter) {
        if (!List.of(",", ";").contains(delimiter)) throw ApiException.invalid("El delimitador debe ser coma o punto y coma.");
        var pending = new ArrayList<Pending>();
        var errors = new ArrayList<RowError>();
        var seen = new HashSet<String>();
        int read = 0, skipped = 0, created = 0, failed = 0;
        // Primero se analiza todo el archivo acotado: encabezados/límites/sintaxis inválidos no escriben datos.
        try (var reader = new PushbackReader(new InputStreamReader(stream, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)), 1)) {
            int first = reader.read();
            if (first != -1 && first != 0xFEFF) reader.unread(first);
            var format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter.charAt(0)).setHeader()
                .setSkipHeaderRecord(true).setIgnoreEmptyLines(false).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get();
            try (var parser = format.parse(reader)) {
                var expected = validator.schema().fields().stream().map(f -> f.name()).toList();
                var headers = parser.getHeaderNames().stream()
                    .map(name -> validator.schema().generatedId() ? name.strip() : name).toList();
                var dataHeaders = headers.stream().filter(name -> !(validator.schema().generatedId() && name.equals(validator.schema().idField()))).toList();
                if (new HashSet<>(headers).size() != headers.size() || dataHeaders.size() != expected.size()
                        || !new HashSet<>(dataHeaders).equals(new HashSet<>(expected)))
                    throw ApiException.invalid("Encabezados incorrectos. Se esperan: " + String.join(", ", expected));
                for (var row : parser) {
                    read++;
                    long number = row.getRecordNumber() + 1;
                    if (read > maxRows) throw ApiException.invalid("El CSV supera el límite de " + maxRows + " filas. No se escribió ningún registro.");
                    try {
                        if (!row.isConsistent()) throw ApiException.invalid("La cantidad de columnas no coincide con los encabezados.");
                        var data = validator.validate(row.toMap());
                        if (validator.schema().generatedId() && validator.id(data) == null)
                            data.put(validator.schema().idField(), csvId(data));
                        if (!seen.add(validator.id(data))) throw ApiException.duplicate();
                        pending.add(new Pending(number, data));
                    } catch (ApiException e) {
                        skipped++; errors.add(new RowError(number, e.getMessage()));
                    }
                }
            }
        } catch (ApiException e) { throw e;
        } catch (IOException | IllegalArgumentException | UncheckedIOException e) {
            throw ApiException.invalid("CSV inválido: revisa UTF-8, comillas, encabezados y delimitador. No se escribió ningún registro.");
        }
        for (var row : pending) {
            try {
                repository.create(validator.id(row.data()), row.data());
                created++;
            } catch (ApiException e) {
                if (e.status() == HttpStatus.CONFLICT) { skipped++; errors.add(new RowError(row.row(), e.getMessage())); }
                else { failed++; errors.add(new RowError(row.row(), e.getMessage())); }
            } catch (RuntimeException e) {
                failed++;
                errors.add(new RowError(row.row(), "No se confirmó la escritura. Consulta el identificador antes de reintentar."));
            }
        }
        return new ImportResult(read, created, skipped, failed, List.copyOf(errors));
    }
    // ID determinista por contenido, para que reimportar el mismo CSV no sobrescriba ni duplique.
    // No supone que Correo sea único ni modifica documentos existentes con IDs automáticos.
    private String csvId(Map<String, Object> data) {
        var content = new StringBuilder();
        for (var field : validator.schema().fields()) {
            String value = String.valueOf(data.get(field.name()));
            content.append(value.length()).append(':').append(value);
        }
        try {
            return "csv-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
