package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
)

// Settings son las preferencias del programa. Se guardan en %LOCALAPPDATA%\ActaConexion.
type Settings struct {
	Auto       bool `json:"auto"`       // activar solo al conectar el móvil
	Background bool `json:"background"` // seguir vigilando al cerrar la ventana
	OpenAfter  bool `json:"openAfter"`  // abrir Acta en el móvil tras activar por primera vez
	Autostart  bool `json:"autostart"`  // iniciar con Windows (se lee del registro, no de aquí)
	Done       bool `json:"done"`       // ya se activó alguna vez (oculta la guía inicial)
}

var (
	setMu sync.Mutex
	cfg   = Settings{Auto: true, Background: true}
)

func settingsPath() string { return filepath.Join(dataDir(), "ajustes.json") }

func loadSettings() {
	setMu.Lock()
	defer setMu.Unlock()
	b, err := os.ReadFile(settingsPath())
	if err == nil {
		_ = json.Unmarshal(b, &cfg)
	}
	cfg.Autostart = autostartGet()
}

func getSettings() Settings {
	setMu.Lock()
	defer setMu.Unlock()
	return cfg
}

func updateSettings(f func(*Settings)) Settings {
	setMu.Lock()
	f(&cfg)
	c := cfg
	setMu.Unlock()
	_ = os.MkdirAll(dataDir(), 0o755)
	if b, err := json.MarshalIndent(c, "", "  "); err == nil {
		_ = os.WriteFile(settingsPath(), b, 0o644)
	}
	return c
}
