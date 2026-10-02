package com.groupa13.disasterrelief.model.core;

public record EntityOption(int id, String displayId, String name) {

    @Override
    public String toString() {
        return displayId + " — " + name;
    }
}
