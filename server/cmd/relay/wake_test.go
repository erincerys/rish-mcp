package main

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestNtfyWakerPublishesWithAuthAndRateLimit(t *testing.T) {
	var posts atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		if r.Method != http.MethodPost || r.URL.Path != "/rish-wake" || string(body) != "wake" {
			t.Errorf("unexpected request %s %s %q", r.Method, r.URL.Path, body)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer tk_test" {
			t.Errorf("Authorization = %q", got)
		}
		posts.Add(1)
	}))
	defer srv.Close()

	wake := ntfyWaker(srv.URL+"/rish-wake", "tk_test", time.Hour)
	wake(context.Background())
	wake(context.Background())
	if n := posts.Load(); n != 1 {
		t.Fatalf("published %d times, want 1 within the rate limit", n)
	}
}

func TestLoadConfigWake(t *testing.T) {
	base := map[string]string{"AI_TOKEN": "a", "DEVICE_TOKEN": "d"}
	withRelayEnv(t, base, func() {
		cfg, err := loadConfigFromEnv()
		if err != nil || cfg.wakeURL != "" || cfg.wakeWait != 30*time.Second {
			t.Fatalf("defaults: %+v, %v", cfg, err)
		}
	})
	withRelayEnv(t, map[string]string{
		"AI_TOKEN": "a", "DEVICE_TOKEN": "d",
		"WAKE_NTFY_URL": "https://ntfy.example.com/rish-wake", "WAKE_NTFY_TOKEN": "tk", "WAKE_WAIT_MS": "5000",
	}, func() {
		cfg, err := loadConfigFromEnv()
		if err != nil || cfg.wakeURL != "https://ntfy.example.com/rish-wake" || cfg.wakeToken != "tk" || cfg.wakeWait != 5*time.Second {
			t.Fatalf("configured: %+v, %v", cfg, err)
		}
	})
	withRelayEnv(t, map[string]string{"AI_TOKEN": "a", "DEVICE_TOKEN": "d", "WAKE_NTFY_URL": "rish-wake"}, func() {
		if _, err := loadConfigFromEnv(); err == nil {
			t.Fatal("relative WAKE_NTFY_URL accepted")
		}
	})
}
