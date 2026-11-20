package main

import (
	"archive/zip"
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

const platformToolsURL = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"

func adbName() string {
	if runtime.GOOS == "windows" {
		return "adb.exe"
	}
	return "adb"
}

// findAdb busca un adb ya instalado: junto al programa, en la carpeta propia, en el PATH
// y en los sitios habituales (la carpeta platform-tools que se descarga en el tutorial de Acta).
func findAdb() string {
	var dirs []string
	if exe, err := os.Executable(); err == nil {
		d := filepath.Dir(exe)
		dirs = append(dirs, filepath.Join(d, "platform-tools"), filepath.Join(d, "adb"), d)
	}
	dirs = append(dirs, filepath.Join(dataDir(), "platform-tools"))
	home, _ := os.UserHomeDir()
	if home != "" {
		for _, n := range []string{"Downloads", "Descargas", "Desktop", "Escritorio", "Documents", "Documentos"} {
			dirs = append(dirs, filepath.Join(home, n, "platform-tools"))
		}
		dirs = append(dirs, filepath.Join(home, "platform-tools"), filepath.Join(home, "AppData", "Local", "Android", "Sdk", "platform-tools"))
	}
	if h := os.Getenv("ANDROID_HOME"); h != "" {
		dirs = append(dirs, filepath.Join(h, "platform-tools"))
	}
	if h := os.Getenv("ANDROID_SDK_ROOT"); h != "" {
		dirs = append(dirs, filepath.Join(h, "platform-tools"))
	}
	if runtime.GOOS == "windows" {
		dirs = append(dirs, `C:\platform-tools`, `C:\adb`)
	}
	for _, d := range dirs {
		p := filepath.Join(d, adbName())
		if st, err := os.Stat(p); err == nil && !st.IsDir() {
			return p
		}
	}
	if p, err := exec.LookPath(adbName()); err == nil {
		return p
	}
	return ""
}

var adbMu sync.Mutex

// runAdb ejecuta adb con un tiempo máximo y sin abrir ventanas de consola.
func runAdb(adb string, timeout time.Duration, args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	c := exec.CommandContext(ctx, adb, args...)
	hideCmd(c)
	var out bytes.Buffer
	c.Stdout = &out
	c.Stderr = &out
	err := c.Run()
	if ctx.Err() != nil {
		return out.String(), errors.New("adb no respondió a tiempo")
	}
	return strings.TrimSpace(out.String()), err
}

// Dev es un móvil visto por adb.
type Dev struct {
	Serial string `json:"serial"`
	State  string `json:"state"` // device, unauthorized, offline
	Model  string `json:"model"`
}

func parseDevices(out string) []Dev {
	var devs []Dev
	for _, ln := range strings.Split(out, "\n") {
		ln = strings.TrimSpace(ln)
		if ln == "" || strings.HasPrefix(ln, "List of devices") || strings.HasPrefix(ln, "*") {
			continue
		}
		f := strings.Fields(ln)
		if len(f) < 2 {
			continue
		}
		d := Dev{Serial: f[0], State: f[1]}
		if d.State == "no" && len(f) > 2 { // "no permissions"
			d.State = "nopermissions"
		}
		for _, x := range f[2:] {
			if strings.HasPrefix(x, "model:") {
				d.Model = strings.ReplaceAll(strings.TrimPrefix(x, "model:"), "_", " ")
			}
		}
		devs = append(devs, d)
	}
	return devs
}

// isUSB descarta los móviles por red y los emuladores: solo interesa el cable.
func isUSB(s string) bool {
	return !strings.Contains(s, ":") && !strings.HasPrefix(s, "emulator-")
}

// downloadAdb baja las herramientas oficiales de Android (platform-tools) desde Google.
func downloadAdb(progress func(pct int)) (string, error) {
	dest := dataDir()
	if err := os.MkdirAll(dest, 0o755); err != nil {
		return "", err
	}
	cl := &http.Client{Timeout: 5 * time.Minute}
	resp, err := cl.Get(platformToolsURL)
	if err != nil {
		return "", fmt.Errorf("no se pudo conectar con Google: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", fmt.Errorf("Google respondió con el error %d", resp.StatusCode)
	}
	var buf bytes.Buffer
	total := resp.ContentLength
	var got int64
	chunk := make([]byte, 64*1024)
	for {
		n, rerr := resp.Body.Read(chunk)
		if n > 0 {
			buf.Write(chunk[:n])
			got += int64(n)
			if total > 0 && progress != nil {
				progress(int(got * 100 / total))
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			return "", fmt.Errorf("la descarga se cortó: %v", rerr)
		}
	}
	zr, err := zip.NewReader(bytes.NewReader(buf.Bytes()), int64(buf.Len()))
	if err != nil {
		return "", fmt.Errorf("el archivo descargado no es válido: %v", err)
	}
	base, _ := filepath.Abs(dest)
	for _, f := range zr.File {
		target := filepath.Join(base, filepath.FromSlash(f.Name))
		if !strings.HasPrefix(target, base+string(os.PathSeparator)) { // protege de rutas maliciosas
			continue
		}
		if f.FileInfo().IsDir() {
			_ = os.MkdirAll(target, 0o755)
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return "", err
		}
		rc, err := f.Open()
		if err != nil {
			return "", err
		}
		w, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o755)
		if err != nil {
			rc.Close()
			return "", err
		}
		_, err = io.Copy(w, rc)
		rc.Close()
		w.Close()
		if err != nil {
			return "", err
		}
	}
	p := filepath.Join(base, "platform-tools", adbName())
	if _, err := os.Stat(p); err != nil {
		return "", errors.New("la descarga no contiene adb")
	}
	return p, nil
}
