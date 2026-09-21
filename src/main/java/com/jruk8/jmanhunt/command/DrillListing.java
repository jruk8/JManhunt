package com.jruk8.jmanhunt.command;

import java.util.List;

/**
 * Next drill level under a resolved config path: sub-section names without
 * values versus editable leaf names that show their values.
 */
public record DrillListing(List<String> sections, List<String> leaves) {
}
