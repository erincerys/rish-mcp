package main

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestWakeBodyMatchesAgentVector(t *testing.T) {
	const want = "1700000000:73ab80c36313b7e89adc8969e43cb072bc3d51089169c5ce2d10b76888e680de"
	if got := wakeBody("tok", time.Unix(1_700_000_000, 0)); got != want {
		t.Fatalf("wakeBody = %q, want %q", got, want)
	}
}

func TestNtfyWakerPublishesSignedBodyWithAuthAndRateLimit(t *testing.T) {
	var posts atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		sentAt, _, _ := strings.Cut(string(body), ":")
		unix, err := strconv.ParseInt(sentAt, 10, 64)
		if err != nil || string(body) != wakeBody("device_tok", time.Unix(unix, 0)) || time.Since(time.Unix(unix, 0)) > time.Minute {
			t.Errorf("body %q is not a fresh wake signed with the device token", body)
		}
		if r.Method != http.MethodPost || r.URL.Path != "/rish-wake" {
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer tk_test" {
			t.Errorf("Authorization = %q", got)
		}
		posts.Add(1)
	}))
	defer srv.Close()

	wake := ntfyWaker(srv.URL+"/rish-wake", "tk_test", "device_tok", time.Hour)
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
