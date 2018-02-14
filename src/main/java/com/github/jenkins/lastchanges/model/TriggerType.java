package com.github.jenkins.lastchanges.model;

/**
 * Created by pestano on 20/03/16.
 */
public enum TriggerType {

    CONTROLLER_FILE("Controller File Change"), COMMIT_MESSAGE("Commit Message");

    public final String name;

    TriggerType(String value) {
        this.name = value;
    }

    public String getName() {
        return name;
    }
}
