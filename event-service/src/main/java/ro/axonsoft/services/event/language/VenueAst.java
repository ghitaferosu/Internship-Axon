package ro.axonsoft.services.event.language;

import java.util.List;

public record VenueAst(String name, List<SeatedSectionAst> sections) {}
