package libcore

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"libcore/device"
	"net"
	"net/http"
	"net/http/httptrace"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing-box/boxapi"
	"github.com/sagernet/sing-box/protocol/group"
	N "github.com/sagernet/sing/common/network"
)

type diagnosticResult struct {
	Phase        string `json:"phase"`
	StatusCode   int    `json:"statusCode"`
	ElapsedMs    int64  `json:"elapsedMs"`
	DialMs       int64  `json:"dialMs"`
	TLSMs        int64  `json:"tlsMs"`
	SelectedNode string `json:"selectedNode,omitempty"`
}

func DiagnoseConnection(instance *BoxInstance, link string, timeout int32, includeSelectedNode bool) (report string, err error) {
	defer device.DeferPanicToError("box.DiagnoseConnection", func(cause error) { err = cause })
	if instance == nil {
		return "", errors.New("core not started")
	}
	var selectedNode string
	if includeSelectedNode {
		if selector, ok := instance.Outbound().Default().(*group.Selector); ok {
			if selected := selector.Selected(N.NetworkTCP); selected != nil {
				selectedNode = selected.Tag()
			}
		}
	}
	result := diagnoseHTTP(link, time.Duration(timeout)*time.Millisecond, func(ctx context.Context, network, addr string) (net.Conn, error) {
		return boxapi.DialContext(ctx, instance.Box, nil, network, addr)
	})
	result.SelectedNode = selectedNode
	data, err := json.Marshal(result)
	return string(data), err
}

func diagnoseHTTP(link string, timeout time.Duration, dial func(context.Context, string, string) (net.Conn, error)) diagnosticResult {
	result := diagnosticResult{Phase: "url", DialMs: -1, TLSMs: -1}
	request, err := http.NewRequest(http.MethodGet, link, nil)
	if err != nil || request.URL.Hostname() == "" ||
		(request.URL.Scheme != "http" && request.URL.Scheme != "https") {
		return result
	}

	var dialNs, tlsStart, tlsNs atomic.Int64
	var dialFailedDNS, tlsStarted, tlsOK atomic.Bool
	dialNs.Store(-1)
	tlsNs.Store(-1)
	start := time.Now()
	trace := &httptrace.ClientTrace{
		TLSHandshakeStart: func() {
			tlsStart.Store(time.Since(start).Nanoseconds())
			tlsStarted.Store(true)
		},
		TLSHandshakeDone: func(_ tls.ConnectionState, err error) {
			if err == nil {
				tlsNs.Store(time.Since(start).Nanoseconds() - tlsStart.Load())
				tlsOK.Store(true)
			}
		},
	}
	transport := &http.Transport{
		DisableKeepAlives: true,
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			dialStart := time.Now()
			conn, err := dial(ctx, network, addr)
			if err == nil {
				dialNs.Store(time.Since(dialStart).Nanoseconds())
			} else {
				var dnsError *net.DNSError
				dialFailedDNS.Store(errors.As(err, &dnsError))
			}
			return conn, err
		},
	}
	defer transport.CloseIdleConnections()
	client := &http.Client{
		Transport: transport,
		Timeout:   timeout,
		CheckRedirect: func(*http.Request, []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}
	if timeout <= 0 {
		client.Timeout = 3 * time.Second
	}
	response, err := client.Do(request.WithContext(httptrace.WithClientTrace(request.Context(), trace)))
	result.ElapsedMs = time.Since(start).Milliseconds()
	if ns := dialNs.Load(); ns >= 0 {
		result.DialMs = time.Duration(ns).Milliseconds()
	}
	if ns := tlsNs.Load(); ns >= 0 {
		result.TLSMs = time.Duration(ns).Milliseconds()
	}
	if err == nil {
		response.Body.Close()
		result.StatusCode = response.StatusCode
		result.Phase = "http"
		if response.StatusCode >= 200 && response.StatusCode < 300 {
			result.Phase = "ok"
		}
		return result
	}

	var dnsError *net.DNSError
	switch {
	case dialFailedDNS.Load() || errors.As(err, &dnsError):
		result.Phase = "dns"
	case result.DialMs < 0:
		result.Phase = "dial"
	case request.URL.Scheme == "https" && (!tlsStarted.Load() || !tlsOK.Load()):
		result.Phase = "tls"
	default:
		result.Phase = "http"
	}
	return result
}
