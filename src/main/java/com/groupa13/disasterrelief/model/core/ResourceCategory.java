package com.groupa13.disasterrelief.model.core;

public record ResourceCategory(
        int categoryId,
        String name,
        Integer parentId,
        int depth,
        String path) {

    @Override
    public String toString() {
        return path;
    }
}
