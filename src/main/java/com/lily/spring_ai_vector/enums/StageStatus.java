package com.lily.spring_ai_vector.enums;

public enum StageStatus {
    PASSED("통과"),
    DETECTED("비속어 탐지"),
    SKIPPED("스킵");

    private final String label;

    StageStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
