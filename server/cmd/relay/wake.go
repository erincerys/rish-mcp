package main

import (
	"context"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"
)

// ntfyWaker returns a waker that publishes to an ntfy topic the Android agent
// listens on, at most once per minInterval.
func ntfyWaker(topicURL, token string, minInterval time.Duration) func(context.Context) {
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

		req, err := http.NewRequestWithContext(ctx, http.MethodPost, topicURL, strings.NewReader("wake"))
		if err != nil {
			log.Printf("wake: %v", err)
			return
		}
		req.Header.Set("Priority", "min")
		if token != "" {
			req.Header.Set("Authorization", "Bearer "+token)
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
