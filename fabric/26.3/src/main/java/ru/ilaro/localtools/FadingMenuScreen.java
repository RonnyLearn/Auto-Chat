package ru.ilaro.localtools;

interface FadingMenuScreen {
    void startClosing();

    void requestUserClose();

    default void commitDraft() {
    }
}
