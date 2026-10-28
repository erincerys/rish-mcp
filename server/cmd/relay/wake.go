package main

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"log"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"
)

// The ntfy app re-broadcasts every message to all apps on the phone, so the
// body is the only proof the relay sent it.
func wakeBody(deviceToken string, now time.Time) string {
	sentAt := strconv.FormatInt(now.Unix(), 10)
	return sentAt + ":" + signWake(deviceToken, sentAt)
}

func signWake(deviceToken, sentAt string) string {
	mac := hmac.New(sha256.New, []byte(deviceToken))
	mac.Write([]byte("rish-wake:" + sentAt))
	return hex.EncodeToString(mac.Sum(nil))
}

func ntfyWaker(topicURL, ntfyToken, deviceToken string, minInterval time.Duration) func(context.Context) {
	client := &http.Client{Timeout: 10 * time.Second}
	var mu sync.Mutex
	var last time.Time
	return func(ctx context.Context) {
		mu.Lock()
		if !last.IsZero() && time.Since(last) < minInterval {
			mu.Unlock()
			return
		}
		last = time.Now()
		mu.Unlock()

		req, err := http.NewRequestWithContext(ctx, http.MethodPost, topicURL, strings.NewReader(wakeBody(deviceToken, time.Now())))
		if err != nil {
			log.Printf("wake: %v", err)
			return
		}
		req.Header.Set("Priority", "min")
		if ntfyToken != "" {
			req.Header.Set("Authorization", "Bearer "+ntfyToken)
		}
		resp, err := client.Do(req)
		if err != nil {
			log.Printf("wake: %v", err)
			return
		}
		resp.Body.Close()
		if resp.StatusCode >= 300 {
			log.Printf("wake: ntfy returned %s", resp.Status)
		}
	}
}
