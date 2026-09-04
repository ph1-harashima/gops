package com.glv.gsysportal.exception;

/** Phase 9-D: PUT to an unknown Manufacturer Channel Master row id. */
public class ManufacturerChannelNotFoundException extends RuntimeException {
    public ManufacturerChannelNotFoundException(Long id) {
        super("Manufacturer Channel not found: " + id);
    }
}
