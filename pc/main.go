// Acta Conexión: activa y mantiene el modo de grabación de las dos voces de Acta desde el PC.
package main

import (
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"
)

const appVersion = "1.0.0"

type runInfo struct {
	Port  int    `json:"port"`
	Token string `json:"token"`
}

func runPath() string { return filepath.Join(dataDir(), "run.json") }

func hasArg(a string) bool {
	for _, x := range os.Args[1:] {
		if x == a {
			return true
		}
	}
	return false
}

// Si ya hay una copia en marcha responde y devuelve su información.
func existingInstance() *runInfo {
	b, err := os.ReadFile(runPath())
	if err != nil {
		return nil
	}
	var ri runInfo
	if json.Unmarshal(b, &ri) != nil || ri.Port == 0 {
		return nil
	}
	cl := &http.Client{Timeout: 2 * time.Second}
	req, _ := http.NewRequest("GET", "http://127.0.0.1:"+itoa(ri.Port)+"/api/state", nil)
	req.Header.Set("X-Token", ri.Token)
	resp, err := cl.Do(req)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, resp.Body)
	if resp.StatusCode != 200 {
		return nil
	}
	return &ri
}

func main() {
	background := hasArg("--background")
	_ = os.MkdirAll(dataDir(), 0o755)
	loadSettings()

	if ri := existingInstance(); ri != nil {
		if !background {
			req, _ := http.NewRequest("POST", "http://127.0.0.1:"+itoa(ri.Port)+"/api/show", nil)
			req.Header.Set("X-Token", ri.Token)
			if resp, err := (&http.Client{Timeout: 3 * time.Second}).Do(req); err == nil {
				resp.Body.Close()
			}
		}
		return
	}

	w := NewWatcher()
	s := &Server{w: w, token: newToken(), quit: make(chan struct{})}
	if err := s.Start(); err != nil {
		os.Exit(1)
	}
	if b, err := json.Marshal(runInfo{Port: s.port, Token: s.token}); err == nil {
		_ = os.WriteFile(runPath(), b, 0o600)
	}
	defer os.Remove(runPath())
	go w.Run()

	var opened time.Time
	hadWindow := false
	show := func() {
		opened = time.Now()
		openWindow(s.URL())
	}
	s.onShow = func() { show() }
	if !background {
		show()
	}

	tick := time.NewTicker(time.Second)
	defer tick.Stop()
	for {
		select {
		case <-s.quit:
			return
		case <-tick.C:
			seen := s.Seen()
			alive := !seen.IsZero() && time.Since(seen) < 8*time.Second
			if alive {
				hadWindow = true
			} else if hadWindow {
				// La ventana se cerró.
				hadWindow = false
				if !getSettings().Background {
					return
				}
			} else if !opened.IsZero() && time.Since(opened) > 60*time.Second && !getSettings().Background {
				return // la ventana no llegó a abrirse
			}
		}
	}
}
