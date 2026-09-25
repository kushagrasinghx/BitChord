package config

import "testing"

func TestGetOrigin(t *testing.T) {
	t.Setenv("JAM_PUBLIC_ORIGIN", " https://party.example.com/ ")
	if got := getOrigin("JAM_PUBLIC_ORIGIN"); got != "https://party.example.com" {
		t.Errorf("Expected https://party.example.com, got %q", got)
	}

	t.Setenv("JAM_PUBLIC_ORIGIN", "party.example.com")
	if got := getOrigin("JAM_PUBLIC_ORIGIN"); got != "" {
		t.Errorf("Expected a non-http(s) origin to be ignored, got %q", got)
	}

	t.Setenv("JAM_PUBLIC_ORIGIN", "https://party.example.com/invite")
	if got := getOrigin("JAM_PUBLIC_ORIGIN"); got != "" {
		t.Errorf("Expected an origin with a path to be ignored, got %q", got)
	}

	t.Setenv("JAM_PUBLIC_ORIGIN", "")
	if got := getOrigin("JAM_PUBLIC_ORIGIN"); got != "" {
		t.Errorf("Expected an unset origin to be empty, got %q", got)
	}
}
