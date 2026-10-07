package co.edu.poligran.registros.dto;

import java.util.List;
import java.util.Map;

public record PageResult(List<Map<String, Object>> items, String nextCursor) {}
