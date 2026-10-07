package presence

import (
	"testing"
	"time"
)

func mustID(t *testing.T, s string) [16]byte {
	t.Helper()
	id, ok := ParseID(s)
	if !ok {
		t.Fatalf("ParseID(%q) rejected a valid UUID", s)
	}
	return id
}

func TestParseID(t *testing.T) {
	valid := []string{
		"3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b",
		"3F2B8C1E-9A4D-4E6F-8B7A-1C2D3E4F5A6B",
	}
	for _, s := range valid {
		if _, ok := ParseID(s); !ok {
			t.Errorf("ParseID(%q) = false, want true", s)
		}
	}
	invalid := []string{
		"",
		"not-a-uuid",
		"3f2b8c1e9a4d4e6f8b7a1c2d3e4f5a6b",
		"3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6",
		"3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6bb",
		"3f2b8c1e-9a4d-4e6f-8b7a_1c2d3e4f5a6b",
		"zf2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b",
	}
	for _, s := range invalid {
		if _, ok := ParseID(s); ok {
			t.Errorf("ParseID(%q) = true, want false", s)
		}
	}
	upper := mustID(t, valid[1])
	lower := mustID(t, valid[0])
	if upper != lower {
		t.Errorf("case should not change the ID")
	}
}

func TestParsePlatform(t *testing.T) {
	if p, ok := ParsePlatform("android"); !ok || p != Android {
		t.Errorf("android not accepted")
	}
	if p, ok := ParsePlatform("pc"); !ok || p != PC {
		t.Errorf("pc not accepted")
	}
	for _, s := range []string{"", "ios", "web", "Android", "PC", "desktop"} {
		if _, ok := ParsePlatform(s); ok {
			t.Errorf("ParsePlatform(%q) = true, want false", s)
		}
	}
}

func TestTrackerCountsAndExpiry(t *testing.T) {
	tr := NewTracker(6*time.Minute, 0)
	a := mustID(t, "00000000-0000-0000-0000-000000000001")
	b := mustID(t, "00000000-0000-0000-0000-000000000002")
	c := mustID(t, "00000000-0000-0000-0000-000000000003")

	if got := tr.Counts(); got.Online != 0 {
		t.Fatalf("fresh tracker reports %d online", got.Online)
	}

	const minute = int64(60_000)
	tr.Ping(a, Android, 0)
	tr.Ping(b, Android, 2*minute)
	tr.Ping(c, PC, 3*minute)
	// Pinging again must not count the same install twice.
	tr.Ping(a, Android, 4*minute)
	tr.Sweep(5 * minute)
	if got := tr.Counts(); got.Online != 3 || got.Android != 2 || got.PC != 1 {
		t.Fatalf("got %+v, want 3 online (2 android, 1 pc)", got)
	}

	// b last pinged at minute 2; at minute 8.5 it is past its 6-minute window.
	tr.Sweep(8*minute + minute/2)
	if got := tr.Counts(); got.Online != 2 || got.Android != 1 || got.PC != 1 {
		t.Fatalf("got %+v, want b expired", got)
	}

	tr.Close(c)
	tr.Sweep(9 * minute)
	if got := tr.Counts(); got.Online != 1 || got.PC != 0 {
		t.Fatalf("got %+v, want c removed by Close", got)
	}
}

func TestTrackerCap(t *testing.T) {
	tr := NewTracker(time.Minute, 2)
	a := mustID(t, "00000000-0000-0000-0000-00000000000a")
	b := mustID(t, "00000000-0000-0000-0000-00000000000b")
	c := mustID(t, "00000000-0000-0000-0000-00000000000c")
	tr.Ping(a, Android, 0)
	tr.Ping(b, PC, 0)
	tr.Ping(c, PC, 0)
	// A known install can still refresh while the tracker is full.
	tr.Ping(a, Android, 30_000)
	tr.Sweep(30_000)
	if got := tr.Counts(); got.Online != 2 {
		t.Fatalf("got %d online, want cap of 2", got.Online)
	}
	// Once b expires there is room again.
	tr.Sweep(70_000)
	tr.Ping(c, PC, 70_000)
	tr.Sweep(70_000)
	if got := tr.Counts(); got.Online != 2 || got.Android != 1 || got.PC != 1 {
		t.Fatalf("got %+v, want a and c", got)
	}
}
