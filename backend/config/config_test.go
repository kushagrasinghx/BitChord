package config

import (
	"testing"
)

func TestGetInt(t *testing.T) {
	t.Setenv("BITCHORD_TEST_INT", "")
	if got := getInt("BITCHORD_TEST_INT", 7); got != 7 {
		t.Errorf("unset = %d, want 7", got)
	}
	t.Setenv("BITCHORD_TEST_INT", "25")
	if got := getInt("BITCHORD_TEST_INT", 7); got != 25 {
		t.Errorf("got %d, want 25", got)
	}
	t.Setenv("BITCHORD_TEST_INT", "  30 ")
	if got := getInt("BITCHORD_TEST_INT", 7); got != 30 {
		t.Errorf("padded value = %d, want 30", got)
	}
	t.Setenv("BITCHORD_TEST_INT", "not-a-number")
	if got := getInt("BITCHORD_TEST_INT", 7); got != 7 {
		t.Errorf("malformed = %d, want fallback 7", got)
	}
}

func TestGetCSV(t *testing.T) {
	t.Setenv("BITCHORD_TEST_CSV", "")
	if got := getCSV("BITCHORD_TEST_CSV", ""); got != nil {
		t.Errorf("empty = %v, want nil", got)
	}
	t.Setenv("BITCHORD_TEST_CSV", "https://a.example, https://b.example")
	got := getCSV("BITCHORD_TEST_CSV", "")
	if len(got) != 2 || got[0] != "https://a.example" || got[1] != "https://b.example" {
		t.Errorf("got %v, want two trimmed origins", got)
	}
	t.Setenv("BITCHORD_TEST_CSV", "a,, ,b")
	got = getCSV("BITCHORD_TEST_CSV", "")
	if len(got) != 2 {
		t.Errorf("blank entries not skipped: %v", got)
	}
	t.Setenv("BITCHORD_TEST_CSV", "")
	got = getCSV("BITCHORD_TEST_CSV", "x,y")
	if len(got) != 2 {
		t.Errorf("fallback not used: %v", got)
	}
}

func TestGetBool(t *testing.T) {
	t.Setenv("BITCHORD_TEST_BOOL", "")
	if got := getBool("BITCHORD_TEST_BOOL", true); got != true {
		t.Errorf("unset = %v, want fallback true", got)
	}
	t.Setenv("BITCHORD_TEST_BOOL", "true")
	if got := getBool("BITCHORD_TEST_BOOL", false); got != true {
		t.Errorf("got %v, want true", got)
	}
	t.Setenv("BITCHORD_TEST_BOOL", "0")
	if got := getBool("BITCHORD_TEST_BOOL", true); got != false {
		t.Errorf("got %v, want false", got)
	}
	t.Setenv("BITCHORD_TEST_BOOL", "yes-please")
	if got := getBool("BITCHORD_TEST_BOOL", true); got != true {
		t.Errorf("malformed = %v, want fallback true", got)
	}
}

func TestIsAllowedOrigin(t *testing.T) {
	old := AllowedOrigins
	AllowedOrigins = []string{"https://app.example", "https://admin.example"}
	defer func() { AllowedOrigins = old }()

	if !IsAllowedOrigin("https://app.example") {
		t.Errorf("listed origin rejected")
	}
	if IsAllowedOrigin("https://evil.example") {
		t.Errorf("unlisted origin allowed")
	}
	if IsAllowedOrigin("") {
		t.Errorf("empty origin allowed")
	}
}
