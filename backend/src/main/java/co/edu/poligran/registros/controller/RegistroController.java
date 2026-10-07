package co.edu.poligran.registros.controller;

import co.edu.poligran.registros.dto.*;
import co.edu.poligran.registros.error.ApiException;
import co.edu.poligran.registros.repository.RegistroRepository;
import co.edu.poligran.registros.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RegistroController {
    private final RegistroRepository repository;
    private final RegistroValidator validator;
    private final CsvImportService importer;
    public RegistroController(RegistroRepository repository, RegistroValidator validator, CsvImportService importer) {
        this.repository = repository; this.validator = validator; this.importer = importer;
    }
    @GetMapping("/esquema")
    public Schema schema() { return validator.schema(); }
    @GetMapping("/registros")
    public PageResult list(@RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor) {
        if (limit < 1 || limit > 100) throw ApiException.invalid("El tamaño de página debe estar entre 1 y 100.");
        if (cursor != null) validator.validateId(cursor);
        return repository.list(limit, cursor);
    }
    @GetMapping("/registros/{id}")
    public Map<String, Object> get(@PathVariable String id) { return repository.get(validator.validateId(id)); }
    @PostMapping("/registros")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> input) {
        var data = validator.validate(input);
        if (validator.schema().generatedId() && validator.id(data) == null)
            data.put(validator.schema().idField(), repository.newId());
        repository.create(validator.id(data), data);
        return ResponseEntity.status(201).body(data);
    }
    @PutMapping("/registros/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> input) {
        validator.validateId(id);
        var data = validator.validate(input);
        if (validator.schema().generatedId() && validator.id(data) == null)
            data.put(validator.schema().idField(), id);
        if (!id.equals(validator.id(data))) throw ApiException.invalid("El identificador no se puede modificar.");
        repository.update(id, data);
        return data;
    }
    @DeleteMapping("/registros/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) { repository.delete(validator.validateId(id)); }
    @PostMapping(value = "/registros/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = ",") String delimiter) throws IOException {
        if (file.isEmpty()) throw ApiException.invalid("Selecciona un CSV que no esté vacío.");
        return importer.importCsv(file.getInputStream(), delimiter);
    }
}
