package com.acta.recorder;

/** Datos del proyecto de código abierto. build.sh sustituye REPO por el repositorio real (ACTA_REPO). */
final class Project {
    static final String REPO = "actaapp-es/acta";
    static final String URL_REPO = "https://github.com/" + REPO;
    static final String URL_PC = URL_REPO + "/releases/latest/download/ActaConexion-Windows.zip";
    static final String URL_SITE = URL_REPO;
    private Project() { }
}
