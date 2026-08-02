package com.dezxxx.minios3.exception;

/**
 * Thrown at startup when a development default is still in place outside the dev profile.
 * Deliberately fatal: a working default is worse than no default, because nothing
 * ever reminds you it is there.
 */
public class InsecureConfigurationException extends RuntimeException {

    public InsecureConfigurationException(String setting, String variable) {
        super(("%s still holds its development default. Set %s before starting outside the dev "
                + "profile, or run with SPRING_PROFILES_ACTIVE=dev if this is a local machine.")
                .formatted(setting, variable));
    }
}