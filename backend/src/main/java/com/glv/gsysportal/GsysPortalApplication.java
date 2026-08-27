package com.glv.gsysportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * G-SYS Online Ordering Prototype - New Service API entry point.
 *
 * This application is entirely separate from the Legacy G-SYS (phasep-gulliver).
 * It reads Legacy data READ ONLY via {@link com.glv.gsysportal.repository.legacy}
 * and owns its own Prototype database via {@link com.glv.gsysportal.repository.prototype}.
 *
 * See docs/G-SYS_Online-Ordering_Prototype_Technical_Design.md.
 */
@SpringBootApplication
public class GsysPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(GsysPortalApplication.class, args);
    }
}
