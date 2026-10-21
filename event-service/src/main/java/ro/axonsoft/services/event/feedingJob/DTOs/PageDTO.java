package ro.axonsoft.services.event.feedingJob.DTOs;

import java.util.List;

public record PageDTO<T>(
        List<T> content,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {
    public static <T> PageDTO<T> of(List<T> allItems, int page, int pageSize) {
        int totalElements = allItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        int fromIndex = Math.min(page * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        List<T> content = allItems.subList(fromIndex, toIndex);

        return new PageDTO<>(content, page, pageSize, totalElements, totalPages);
    }
}