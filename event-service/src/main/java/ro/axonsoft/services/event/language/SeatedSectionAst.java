package ro.axonsoft.services.event.language;

public record SeatedSectionAst(
        String name,
        Direction direction,
        int rowStart, int rowEnd,
        int seatStart, int seatEnd,
        int line, int col
) {}
