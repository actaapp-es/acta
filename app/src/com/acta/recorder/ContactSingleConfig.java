package com.acta.recorder;

import android.appwidget.AppWidgetManager;

/** Elegir el contacto del widget de 1×1. */
public class ContactSingleConfig extends ContactsWidgetConfig {
    @Override protected int maxContacts() { return 1; }
    @Override protected String heading() { return "Contacto del widget"; }
    @Override protected String hint() { return "Elige un contacto. Al pulsarlo en el widget eliges: llamar, WhatsApp o historial."; }
    @Override protected void applyWidget() { ContactSingleWidget.update(this, AppWidgetManager.getInstance(this), widgetIdValue()); }
}
