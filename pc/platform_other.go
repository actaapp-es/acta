//go:build !windows

package main

import (
	"os"
	"os/exec"
	"path/filepath"
)

// Versión para probar la lógica fuera de Windows.

var autostartFake bool

func hideCmd(c *exec.Cmd) {}

func dataDir() string {
	if d := os.Getenv("ACTA_DATA"); d != "" {
		return d
	}
	h, _ := os.UserHomeDir()
	return filepath.Join(h, ".local", "share", "ActaConexion")
}

func openWindow(url string) {
	if os.Getenv("ACTA_NO_WINDOW") == "" {
		println("abrir:", url)
	}
}

func autostartGet() bool   { return autostartFake }
func autostartSet(on bool) { autostartFake = on }
