//go:build windows

package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
)

const createNoWindow = 0x08000000

func hideCmd(c *exec.Cmd) {
	c.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: createNoWindow}
}

func dataDir() string {
	if d := os.Getenv("LOCALAPPDATA"); d != "" {
		return filepath.Join(d, "ActaConexion")
	}
	h, _ := os.UserHomeDir()
	return filepath.Join(h, "AppData", "Local", "ActaConexion")
}

func browserPath() string {
	var c []string
	for _, base := range []string{os.Getenv("ProgramFiles(x86)"), os.Getenv("ProgramFiles"), os.Getenv("LOCALAPPDATA")} {
		if base == "" {
			continue
		}
		c = append(c,
			filepath.Join(base, `Microsoft\Edge\Application\msedge.exe`),
			filepath.Join(base, `Google\Chrome\Application\chrome.exe`))
	}
	for _, p := range c {
		if st, err := os.Stat(p); err == nil && !st.IsDir() {
			return p
		}
	}
	return ""
}

// openWindow abre la interfaz como una ventana propia (Edge en modo aplicación, sin barra de direcciones).
func openWindow(url string) {
	if b := browserPath(); b != "" {
		profile := filepath.Join(dataDir(), "ventana")
		c := exec.Command(b, "--app="+url, "--window-size=1040,780", "--user-data-dir="+profile,
			"--no-first-run", "--no-default-browser-check", "--disable-extensions", "--disable-sync")
		if c.Start() == nil {
			return
		}
	}
	// Sin Edge ni Chrome: navegador predeterminado.
	c := exec.Command(filepath.Join(os.Getenv("SystemRoot"), "System32", "rundll32.exe"), "url.dll,FileProtocolHandler", url)
	hideCmd(c)
	_ = c.Start()
}

const runKey = `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`

func regExe() string { return filepath.Join(os.Getenv("SystemRoot"), "System32", "reg.exe") }

func autostartGet() bool {
	c := exec.Command(regExe(), "query", runKey, "/v", "ActaConexion")
	hideCmd(c)
	out, err := c.Output()
	return err == nil && strings.Contains(string(out), "ActaConexion")
}

func autostartSet(on bool) {
	var c *exec.Cmd
	if on {
		exe, err := os.Executable()
		if err != nil {
			return
		}
		c = exec.Command(regExe(), "add", runKey, "/v", "ActaConexion", "/t", "REG_SZ", "/d", `"`+exe+`" --background`, "/f")
	} else {
		c = exec.Command(regExe(), "delete", runKey, "/v", "ActaConexion", "/f")
	}
	hideCmd(c)
	_ = c.Run()
}
