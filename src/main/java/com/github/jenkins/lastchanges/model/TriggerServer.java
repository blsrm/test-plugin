package com.github.jenkins.lastchanges.model;

/**
 * Created by pestano on 20/03/16.
 */
public enum TriggerServer {

    REMOTE_SERVER("Remote Server"), SAME_SERVER("Same Server");

    public final String name;

    TriggerServer(String value) {
        this.name = value;
    }

    public String getName() {
        return name;
    }
}
