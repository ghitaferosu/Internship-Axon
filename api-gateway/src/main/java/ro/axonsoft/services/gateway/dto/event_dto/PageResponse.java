package ro.axonsoft.services.gateway.dto.event_dto;


import java.util.List;

public record PageResponse<T> (
    List<T> content,
    int page,
    int pageSize,
    long totalElements,
    int totalPages
){ }

