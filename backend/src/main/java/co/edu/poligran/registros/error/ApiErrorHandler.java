package co.edu.poligran.registros.error;

import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiErrorHandler {
    public record ErrorBody(String message) {}
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException e) { return ResponseEntity.status(e.status()).body(new ErrorBody(e.getMessage())); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> size() { return ResponseEntity.status(413).body(new ErrorBody("El archivo supera el tamaño permitido.")); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ErrorBody> invalid() { return ResponseEntity.badRequest().body(new ErrorBody("Solicitud inválida: revisa el JSON, los parámetros y el campo file.")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception e) {
        LoggerFactory.getLogger(getClass()).error("Fallo no esperado de la API: {}", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ErrorBody("Ocurrió un error interno. Revisa la terminal del backend."));
    }
}
