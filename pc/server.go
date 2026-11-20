package main

import (
	"crypto/rand"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

//go:embed web/index.html
var indexHTML []byte

//go:embed web/logo.svg
var logoSVG []byte

//go:embed web/favicon.png
var faviconPNG []byte

type Server struct {
	w        *Watcher
	token    string
	port     int
	mu       sync.Mutex
	lastSeen time.Time
	quit     chan struct{}
	onShow   func()
}

func newToken() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func (s *Server) Seen() time.Time {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.lastSeen
}

func (s *Server) hostOK(r *http.Request) bool {
	h := r.Host
	return h == "127.0.0.1:"+itoa(s.port) || h == "localhost:"+itoa(s.port)
}

func itoa(n int) string {
	b, _ := json.Marshal(n)
	return string(b)
}

func (s *Server) guard(next http.HandlerFunc) http.HandlerFunc {
	return func(rw http.ResponseWriter, r *http.Request) {
		// Evita que una página web cualquiera hable con el programa (DNS rebinding / CSRF).
		if !s.hostOK(r) || r.Header.Get("X-Token") != s.token {
			http.Error(rw, "forbidden", http.StatusForbidden)
			return
		}
		rw.Header().Set("Cache-Control", "no-store")
		next(rw, r)
	}
}

func (s *Server) Start() error {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return err
	}
	s.port = ln.Addr().(*net.TCPAddr).Port
	mux := http.NewServeMux()
	static := func(ct string, body []byte) http.HandlerFunc {
		return func(rw http.ResponseWriter, r *http.Request) {
			if !s.hostOK(r) {
				http.Error(rw, "forbidden", http.StatusForbidden)
				return
			}
			rw.Header().Set("Content-Type", ct)
			rw.Header().Set("Cache-Control", "no-store")
			rw.Header().Set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; img-src 'self' data:")
			_, _ = rw.Write(body)
		}
	}
	mux.HandleFunc("/", func(rw http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(rw, r)
			return
		}
		static("text/html; charset=utf-8", indexHTML)(rw, r)
	})
	mux.HandleFunc("/logo.svg", static("image/svg+xml", logoSVG))
	mux.HandleFunc("/favicon.png", static("image/png", faviconPNG))
	mux.HandleFunc("/api/state", s.guard(func(rw http.ResponseWriter, r *http.Request) {
		s.mu.Lock()
		s.lastSeen = time.Now()
		s.mu.Unlock()
		rw.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(rw).Encode(s.w.Snapshot())
	}))
	mux.HandleFunc("/api/action", s.guard(func(rw http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(rw, "method", http.StatusMethodNotAllowed)
			return
		}
		switch r.URL.Query().Get("name") {
		case "activate":
			s.w.RequestNow()
		case "download":
			s.w.StartDownload()
		case "recheck":
			s.w.Poke()
		case "openacta":
			go s.w.OpenActaNow()
		case "quit":
			go func() {
				time.Sleep(200 * time.Millisecond)
				close(s.quit)
			}()
		}
		rw.WriteHeader(http.StatusNoContent)
	}))
	mux.HandleFunc("/api/settings", s.guard(func(rw http.ResponseWriter, r *http.Request) {
		var in map[string]bool
		if r.Method != http.MethodPost || json.NewDecoder(http.MaxBytesReader(rw, r.Body, 4096)).Decode(&in) != nil {
			http.Error(rw, "bad", http.StatusBadRequest)
			return
		}
		updateSettings(func(c *Settings) {
			if v, ok := in["auto"]; ok {
				c.Auto = v
			}
			if v, ok := in["background"]; ok {
				c.Background = v
			}
			if v, ok := in["openAfter"]; ok {
				c.OpenAfter = v
			}
		})
		if v, ok := in["autostart"]; ok {
			autostartSet(v)
			updateSettings(func(c *Settings) { c.Autostart = autostartGet() })
		}
		s.w.Poke()
		rw.WriteHeader(http.StatusNoContent)
	}))
	// Lo usa una segunda copia del programa para pedir que se abra la ventana.
	mux.HandleFunc("/api/show", s.guard(func(rw http.ResponseWriter, r *http.Request) {
		go s.onShow()
		rw.WriteHeader(http.StatusNoContent)
	}))
	srv := &http.Server{Handler: mux, ReadHeaderTimeout: 5 * time.Second}
	go func() { _ = srv.Serve(ln) }()
	return nil
}

func (s *Server) URL() string {
	return "http://127.0.0.1:" + itoa(s.port) + "/#" + s.token
}

func hasPrefixFold(s, p string) bool { return strings.HasPrefix(strings.ToLower(s), p) }
