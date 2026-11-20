package main

import (
	"fmt"
	"strings"
	"sync"
	"time"
)

const tcpPort = "5555"

type LogEntry struct {
	T    string `json:"t"`
	Text string `json:"text"`
	Kind string `json:"kind"` // ok, warn, info
}

// DevView es lo que ve la interfaz de cada móvil.
type DevView struct {
	Serial string `json:"serial"`
	Name   string `json:"name"`
	State  string `json:"state"` // ready, activating, idle, auth, offline, error
	Detail string `json:"detail,omitempty"`
}

type View struct {
	Version     string     `json:"version"`
	Phase       string     `json:"phase"` // noadb, downloading, nodevice, auth, activating, idle, ready, error
	AdbPath     string     `json:"adbPath"`
	DlPct       int        `json:"dlPct"`
	DlErr       string     `json:"dlErr,omitempty"`
	Devices     []DevView  `json:"devices"`
	NoDeviceSec int        `json:"noDeviceSec"`
	WasActive   bool       `json:"wasActive"` // se activó en esta sesión: se puede desconectar el cable
	Settings    Settings   `json:"settings"`
	Log         []LogEntry `json:"log"`
}

type Watcher struct {
	mu          sync.Mutex
	adb         string
	downloading bool
	dlPct       int
	dlErr       string
	devs        []DevView
	log         []LogEntry
	noDevSince  time.Time
	wasActive   bool
	lastTry     map[string]time.Time
	tries       map[string]int
	prevState   map[string]string
	names       map[string]string
	wantNow     bool
	wake        chan struct{}
}

func NewWatcher() *Watcher {
	return &Watcher{lastTry: map[string]time.Time{}, tries: map[string]int{}, prevState: map[string]string{}, names: map[string]string{}, wake: make(chan struct{}, 1)}
}

func (w *Watcher) logf(kind, f string, a ...interface{}) {
	e := LogEntry{T: time.Now().Format("15:04:05"), Text: fmt.Sprintf(f, a...), Kind: kind}
	w.log = append(w.log, e)
	if len(w.log) > 60 {
		w.log = w.log[len(w.log)-60:]
	}
}

func (w *Watcher) Poke() {
	select {
	case w.wake <- struct{}{}:
	default:
	}
}

// RequestNow pide una activación inmediata aunque la automática esté apagada.
func (w *Watcher) RequestNow() {
	w.mu.Lock()
	w.wantNow = true
	for k := range w.lastTry {
		delete(w.lastTry, k)
	}
	for k := range w.tries {
		delete(w.tries, k)
	}
	w.mu.Unlock()
	w.Poke()
}

func (w *Watcher) Run() {
	for {
		w.cycle()
		select {
		case <-time.After(2 * time.Second):
		case <-w.wake:
		}
	}
}

func (w *Watcher) cycle() {
	w.mu.Lock()
	adb := w.adb
	dl := w.downloading
	w.mu.Unlock()
	if dl {
		return
	}
	if adb == "" {
		if p := findAdb(); p != "" {
			w.mu.Lock()
			w.adb = p
			w.logf("info", "Herramientas de Android encontradas")
			w.mu.Unlock()
			adb = p
		} else {
			return
		}
	}
	adbMu.Lock()
	out, err := runAdb(adb, 15*time.Second, "devices", "-l")
	adbMu.Unlock()
	w.mu.Lock()
	if err != nil && out == "" {
		w.logf("warn", "adb no responde: %v", err)
	}
	w.mu.Unlock()
	raw := parseDevices(out)

	var views []DevView
	for _, d := range raw {
		if !isUSB(d.Serial) {
			continue
		}
		views = append(views, w.handle(adb, d))
	}

	w.mu.Lock()
	defer w.mu.Unlock()
	// Móviles que ya no están: se avisa una vez.
	present := map[string]bool{}
	for _, v := range views {
		present[v.Serial] = true
	}
	for s, st := range w.prevState {
		if !present[s] {
			if time.Since(w.lastTry[s]) < 12*time.Second && !w.lastTry[s].IsZero() {
				// El móvil reinicia su servicio adb y desaparece un momento: es normal.
				views = append(views, DevView{Serial: s, Name: w.names[s], State: "activating"})
				continue
			}
			if st == "ready" {
				w.logf("info", "Cable desconectado. Acta sigue conectado hasta que reinicies el móvil.")
			} else {
				w.logf("info", "Móvil desconectado")
			}
			delete(w.prevState, s)
			delete(w.tries, s)
			delete(w.lastTry, s)
		}
	}
	w.devs = views
	if len(views) == 0 {
		if w.noDevSince.IsZero() {
			w.noDevSince = time.Now()
		}
	} else {
		w.noDevSince = time.Time{}
	}
}

// handle decide qué hacer con un móvil y devuelve su estado.
func (w *Watcher) handle(adb string, d Dev) DevView {
	name := w.deviceName(adb, d)
	v := DevView{Serial: d.Serial, Name: name}
	prev := w.prevState[d.Serial]
	set := func(st string) {
		v.State = st
		w.mu.Lock()
		if prev != st {
			switch st {
			case "auth":
				w.logf("warn", "%s detectado: falta que aceptes el aviso en el móvil", name)
			case "offline":
				w.logf("warn", "%s no responde: desconecta y vuelve a conectar el cable", name)
			case "idle":
				w.logf("info", "%s conectado", name)
			}
		}
		w.prevState[d.Serial] = st
		w.mu.Unlock()
	}
	switch d.State {
	case "unauthorized":
		set("auth")
		return v
	case "offline", "nopermissions":
		w.mu.Lock()
		recent := !w.lastTry[d.Serial].IsZero() && time.Since(w.lastTry[d.Serial]) < 12*time.Second
		w.mu.Unlock()
		if recent {
			v.State = "activating"
			return v
		}
		set("offline")
		return v
	case "device":
	default:
		set("offline")
		return v
	}

	adbMu.Lock()
	port, _ := runAdb(adb, 8*time.Second, "-s", d.Serial, "shell", "getprop", "service.adb.tcp.port")
	adbMu.Unlock()
	if strings.TrimSpace(port) == tcpPort {
		w.mu.Lock()
		if prev != "ready" {
			if prev == "activating" {
				w.logf("ok", "Modo de Acta activado en %s (puerto %s)", name, tcpPort)
			} else {
				w.logf("ok", "%s ya tenía el modo de Acta activado", name)
			}
			w.wasActive = true
			delete(w.tries, d.Serial)
		}
		w.mu.Unlock()
		if prev != "ready" {
			first := !getSettings().Done
			updateSettings(func(s *Settings) { s.Done = true })
			if first && getSettings().OpenAfter {
				w.openActa(adb, d.Serial)
			}
		}
		set("ready")
		return v
	}

	// Falta activar.
	w.mu.Lock()
	manual := w.wantNow
	auto := getSettings().Auto
	last := w.lastTry[d.Serial]
	n := w.tries[d.Serial]
	w.mu.Unlock()
	if !auto && !manual {
		set("idle")
		return v
	}
	if time.Since(last) < 12*time.Second {
		set("activating")
		return v
	}
	if n >= 4 {
		set("error")
		v.Detail = "No se consigue activar. Desconecta el cable, vuelve a conectarlo y pulsa «Activar ahora»."
		return v
	}
	set("activating")
	w.mu.Lock()
	w.lastTry[d.Serial] = time.Now()
	w.tries[d.Serial] = n + 1
	w.wantNow = false
	if prev != "activating" {
		w.logf("info", "Activando el modo de Acta en %s…", name)
	}
	w.mu.Unlock()
	adbMu.Lock()
	res, err := runAdb(adb, 15*time.Second, "-s", d.Serial, "tcpip", tcpPort)
	adbMu.Unlock()
	w.mu.Lock()
	if err != nil || !strings.Contains(strings.ToLower(res), "restarting") {
		w.logf("warn", "No se pudo activar (intento %d): %s", n+1, firstLine(res, err))
	}
	w.mu.Unlock()
	// El móvil reinicia su servicio adb: se vuelve a comprobar en el siguiente ciclo.
	time.Sleep(1500 * time.Millisecond)
	return v
}

func firstLine(s string, err error) string {
	s = strings.TrimSpace(s)
	if i := strings.IndexByte(s, '\n'); i >= 0 {
		s = s[:i]
	}
	if s == "" && err != nil {
		return err.Error()
	}
	return s
}

func (w *Watcher) deviceName(adb string, d Dev) string {
	w.mu.Lock()
	if n := w.names[d.Serial]; n != "" {
		w.mu.Unlock()
		return n
	}
	w.mu.Unlock()
	if d.State != "device" {
		if d.Model != "" {
			return d.Model
		}
		return "Tu móvil"
	}
	adbMu.Lock()
	defer adbMu.Unlock()
	for _, p := range []string{"ro.product.marketname", "ro.product.model"} {
		if o, err := runAdb(adb, 6*time.Second, "-s", d.Serial, "shell", "getprop", p); err == nil && strings.TrimSpace(o) != "" {
			n := cleanName(o)
			w.mu.Lock()
			w.names[d.Serial] = n
			w.mu.Unlock()
			return n
		}
	}
	if d.Model != "" {
		return d.Model
	}
	return "Tu móvil"
}

func (w *Watcher) openActa(adb, serial string) {
	adbMu.Lock()
	_, _ = runAdb(adb, 10*time.Second, "-s", serial, "shell", "monkey", "-p", "com.acta.recorder", "-c", "android.intent.category.LAUNCHER", "1")
	adbMu.Unlock()
	w.mu.Lock()
	w.logf("info", "Acta abierto en el móvil")
	w.mu.Unlock()
}

// OpenActaNow abre Acta en el primer móvil listo.
func (w *Watcher) OpenActaNow() {
	w.mu.Lock()
	adb := w.adb
	serial := ""
	for _, v := range w.devs {
		if v.State == "ready" || v.State == "idle" {
			serial = v.Serial
			break
		}
	}
	w.mu.Unlock()
	if adb != "" && serial != "" {
		w.openActa(adb, serial)
	}
}

func (w *Watcher) StartDownload() {
	w.mu.Lock()
	if w.downloading {
		w.mu.Unlock()
		return
	}
	w.downloading = true
	w.dlPct = 0
	w.dlErr = ""
	w.logf("info", "Descargando las herramientas de Android de Google…")
	w.mu.Unlock()
	go func() {
		p, err := downloadAdb(func(pct int) {
			w.mu.Lock()
			w.dlPct = pct
			w.mu.Unlock()
		})
		w.mu.Lock()
		w.downloading = false
		if err != nil {
			w.dlErr = err.Error()
			w.logf("warn", "Descarga fallida: %v", err)
		} else {
			w.adb = p
			w.logf("ok", "Herramientas de Android instaladas")
		}
		w.mu.Unlock()
		w.Poke()
	}()
}

func (w *Watcher) Snapshot() View {
	w.mu.Lock()
	defer w.mu.Unlock()
	v := View{Version: appVersion, AdbPath: w.adb, DlPct: w.dlPct, DlErr: w.dlErr, Devices: w.devs,
		WasActive: w.wasActive, Settings: getSettings(), Log: append([]LogEntry(nil), w.log...)}
	if v.Devices == nil {
		v.Devices = []DevView{}
	}
	if !w.noDevSince.IsZero() {
		v.NoDeviceSec = int(time.Since(w.noDevSince).Seconds())
	}
	switch {
	case w.downloading:
		v.Phase = "downloading"
	case w.adb == "":
		v.Phase = "noadb"
	case len(w.devs) == 0:
		v.Phase = "nodevice"
	default:
		// Gana el estado "más urgente" entre los móviles.
		rank := map[string]int{"ready": 0, "idle": 1, "activating": 2, "offline": 3, "auth": 4, "error": 5}
		best := "ready"
		for _, d := range w.devs {
			if rank[d.State] > rank[best] {
				best = d.State
			}
		}
		v.Phase = best
	}
	return v
}

// cleanName limita y limpia el nombre que declara el móvil.
func cleanName(s string) string {
	s = strings.Map(func(r rune) rune {
		if r < 32 || r == 127 {
			return -1
		}
		return r
	}, strings.TrimSpace(s))
	if len([]rune(s)) > 40 {
		s = string([]rune(s)[:40])
	}
	return s
}
