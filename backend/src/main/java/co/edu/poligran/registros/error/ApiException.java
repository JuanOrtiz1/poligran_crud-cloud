package co.edu.poligran.registros.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    public ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
    public static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    public static ApiException duplicate() { return new ApiException(HttpStatus.CONFLICT, "Ya existe un registro con ese identificador."); }
    public static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "No se encontró el registro."); }
}
