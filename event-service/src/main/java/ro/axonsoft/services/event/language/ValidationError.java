package ro.axonsoft.services.event.language;

public record ValidationError(int line, int col, String message) {}
