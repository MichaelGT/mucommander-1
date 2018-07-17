package com.mucommander.ui.main.table;

public enum SortOrder {
    ASC(true),
    DESC(false);

    private final boolean asc;
    private SortOrder opposite;

    SortOrder(boolean asc) {
        this.asc = asc;
    }

    public boolean isAsc() {
        return asc;
    }

    public SortOrder opposite() {
        if (opposite == null) {
            if (this == ASC) {
                opposite = DESC;
            } else {
                opposite = ASC;
            }
        }
        return opposite;
    }

    public static SortOrder parse(String value) {
        if (value != null) {
            for (SortOrder sortOrder : values()) {
                if (sortOrder.name().equals(value.toUpperCase())) {
                    return sortOrder;
                }
            }
        }
        return SortOrder.ASC;
    }

}
