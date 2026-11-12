package com.limelight.ui;

/**
 * Callbacks from the input layer to the host app's UI.
 *
 * PocketDeck modification: upstream only had toggleKeyboard(). showNotice() and onQuitCombo()
 * were added so the engine never shows its own Toasts or finishes the activity; the host app
 * renders everything itself.
 */
public interface GameGestures {
    void toggleKeyboard();

    /** A short, transient message for the user (replaces upstream Toasts). */
    void showNotice(String message);

    /** Start+Select+LB+RB was pressed and released (upstream finished the activity here). */
    void onQuitCombo();
}
