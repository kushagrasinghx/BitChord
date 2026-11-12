package protocol

import (
	"strings"
	"testing"
)

func validRequest() JoinRequest {
	max := 5
	autoplay := true
	return JoinRequest{
		UserId:          "u_alice",
		DeviceId:        "d_alice",
		DisplayName:     "Alice",
		MaxMembers:      &max,
		AutoplayEnabled: &autoplay,
	}
}

func TestValidateAcceptsValidRequest(t *testing.T) {
	req := validRequest()
	avatar := "https://example.com/a.png"
	req.AvatarUrl = &avatar
	if err := req.Validate(); err != nil {
		t.Fatalf("Validate() = %v, want nil", err)
	}
}

func TestValidateRejectsMissingIdentity(t *testing.T) {
	cases := []struct {
		name   string
		mutate func(*JoinRequest)
	}{
		{"empty userId", func(r *JoinRequest) { r.UserId = "   " }},
		{"empty deviceId", func(r *JoinRequest) { r.DeviceId = "" }},
		{"empty displayName", func(r *JoinRequest) { r.DisplayName = " " }},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			req := validRequest()
			c.mutate(&req)
			if err := req.Validate(); err == nil {
				t.Fatalf("Validate() = nil, want error")
			}
		})
	}
}

func TestValidateRejectsOversizeFields(t *testing.T) {
	req := validRequest()
	req.UserId = strings.Repeat("u", 129)
	if err := req.Validate(); err == nil {
		t.Errorf("oversize userId accepted")
	}

	req = validRequest()
	req.DeviceId = strings.Repeat("d", 129)
	if err := req.Validate(); err == nil {
		t.Errorf("oversize deviceId accepted")
	}

	req = validRequest()
	req.DisplayName = strings.Repeat("n", 81)
	if err := req.Validate(); err == nil {
		t.Errorf("oversize displayName accepted")
	}

	req = validRequest()
	avatar := "https://example.com/" + strings.Repeat("a", 1000)
	req.AvatarUrl = &avatar
	if err := req.Validate(); err == nil {
		t.Errorf("oversize avatarUrl accepted")
	}
}

func TestValidateAvatarUrl(t *testing.T) {
	req := validRequest()
	blank := "   "
	req.AvatarUrl = &blank
	if err := req.Validate(); err != nil {
		t.Fatalf("blank avatarUrl should be dropped, got %v", err)
	}
	if req.AvatarUrl != nil {
		t.Errorf("blank avatarUrl should become nil")
	}

	req = validRequest()
	bad := "ftp://example.com/a.png"
	req.AvatarUrl = &bad
	if err := req.Validate(); err == nil {
		t.Errorf("non-http avatarUrl accepted")
	}
}

func TestValidateMaxMembers(t *testing.T) {
	for _, n := range []int{2, 5, 10} {
		req := validRequest()
		req.MaxMembers = &n
		if err := req.Validate(); err != nil {
			t.Errorf("MaxMembers=%d rejected: %v", n, err)
		}
	}
	for _, n := range []int{1, 11} {
		req := validRequest()
		req.MaxMembers = &n
		if err := req.Validate(); err == nil {
			t.Errorf("MaxMembers=%d accepted, want error", n)
		}
	}
}

func TestValidateTrimsWhitespace(t *testing.T) {
	req := validRequest()
	req.UserId = "  u_alice "
	req.DisplayName = " Alice "
	if err := req.Validate(); err != nil {
		t.Fatalf("Validate() = %v, want nil", err)
	}
	if req.UserId != "u_alice" || req.DisplayName != "Alice" {
		t.Errorf("fields not trimmed: %+v", req)
	}
}
