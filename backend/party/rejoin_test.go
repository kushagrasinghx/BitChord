package party

import (
	"testing"
)

// A device id identifies an install; it is derived from it and is not a secret,
// so it must never be enough to take over a membership [SEC-11].
func newTestParty(t *testing.T) *Party {
	t.Helper()
	store := NewPartyStore()
	p, err := store.Create()
	if err != nil {
		t.Fatalf("create party: %v", err)
	}
	return p
}

func joinErr(t *testing.T, err error) *PartyError {
	t.Helper()
	if err == nil {
		t.Fatalf("expected a rejoin to be refused, got nil error")
	}
	pe, ok := err.(*PartyError)
	if !ok {
		t.Fatalf("expected *PartyError, got %T: %v", err, err)
	}
	return pe
}

func TestRejoinWithoutTokenIsRefusedAndChangesNothing(t *testing.T) {
	p := newTestParty(t)
	host, err := p.Join("u_host", "d_host", "Host", nil, "")
	if err != nil {
		t.Fatalf("initial join: %v", err)
	}
	tokenBefore, nameBefore := host.Token, host.DisplayName

	_, err = p.Join("u_attacker", "d_host", "Attacker", nil, "")
	pe := joinErr(t, err)
	if pe.Status != 401 || pe.Code != "rejoin_denied" {
		t.Fatalf("got %d/%s, want 401/rejoin_denied", pe.Status, pe.Code)
	}

	if host.DisplayName != nameBefore {
		t.Errorf("display name changed on a refused rejoin: %q", host.DisplayName)
	}
	if host.Token != tokenBefore {
		t.Errorf("token changed on a refused rejoin")
	}
	if !host.IsHost {
		t.Errorf("host flag changed on a refused rejoin")
	}
	if len(p.Members) != 1 {
		t.Errorf("member count changed on a refused rejoin: %d", len(p.Members))
	}
}

func TestRejoinWithWrongTokenIsRefused(t *testing.T) {
	p := newTestParty(t)
	host, err := p.Join("u_host", "d_host", "Host", nil, "")
	if err != nil {
		t.Fatalf("initial join: %v", err)
	}

	for _, wrong := range []string{"deadbeef", host.Token + "0", host.Token[:len(host.Token)-1]} {
		_, err := p.Join("u_host", "d_host", "Host", nil, wrong)
		pe := joinErr(t, err)
		if pe.Status != 401 {
			t.Fatalf("token %q: got status %d, want 401", wrong, pe.Status)
		}
	}
}

func TestRejoinWithValidTokenRestoresTheSameMember(t *testing.T) {
	p := newTestParty(t)
	host, err := p.Join("u_host", "d_host", "Host", nil, "")
	if err != nil {
		t.Fatalf("initial join: %v", err)
	}

	again, err := p.Join("u_host", "d_host", "Renamed", nil, host.Token)
	if err != nil {
		t.Fatalf("valid rejoin refused: %v", err)
	}
	if again.MemberId != host.MemberId {
		t.Errorf("rejoin made a new member: %s != %s", again.MemberId, host.MemberId)
	}
	if !again.IsHost {
		t.Errorf("host ownership lost across a valid rejoin")
	}
	if again.Token != host.Token {
		t.Errorf("token rotated on rejoin; a lost response would lock the device out")
	}
	if again.DisplayName != "Renamed" {
		t.Errorf("display name not updated: %q", again.DisplayName)
	}
}

// A token proves one membership, not any membership: neither another member of
// the same party nor the same device id in a different party can use it.
func TestATokenCannotReclaimSomeoneElsesMembership(t *testing.T) {
	p := newTestParty(t)
	host, err := p.Join("u_host", "d_host", "Host", nil, "")
	if err != nil {
		t.Fatalf("host join: %v", err)
	}
	listener, err := p.Join("u_listener", "d_listener", "Listener", nil, "")
	if err != nil {
		t.Fatalf("listener join: %v", err)
	}

	// The listener's token, presented for the host's device.
	if _, err := p.Join("u_listener", "d_host", "Listener", nil, listener.Token); err == nil {
		t.Fatalf("another member's token reclaimed the host membership")
	}
	// The host's token, presented for the listener's device.
	if _, err := p.Join("u_host", "d_listener", "Host", nil, host.Token); err == nil {
		t.Fatalf("the host's token reclaimed the listener membership")
	}

	// A token minted in a different party.
	other := newTestParty(t)
	otherHost, err := other.Join("u_host", "d_other", "Host", nil, "")
	if err != nil {
		t.Fatalf("other party join: %v", err)
	}
	if _, err := p.Join("u_host", "d_host", "Host", nil, otherHost.Token); err == nil {
		t.Fatalf("a token from another party authenticated this membership")
	}
}

func TestANewDeviceStillJoinsWithoutAToken(t *testing.T) {
	p := newTestParty(t)
	if _, err := p.Join("u_host", "d_host", "Host", nil, ""); err != nil {
		t.Fatalf("host join: %v", err)
	}

	listener, err := p.Join("u_listener", "d_listener", "Listener", nil, "")
	if err != nil {
		t.Fatalf("a fresh device must join without a token: %v", err)
	}
	if listener.IsHost {
		t.Errorf("a new device took host")
	}
	if listener.Token == "" {
		t.Errorf("a new membership must be issued a token")
	}
}

// Leaving removes the membership, so its token is gone with it: the same device
// rejoining afterwards is a new member, not a resumption.
func TestLeavingInvalidatesTheOldToken(t *testing.T) {
	p := newTestParty(t)
	host, err := p.Join("u_host", "d_host", "Host", nil, "")
	if err != nil {
		t.Fatalf("host join: %v", err)
	}
	guest, err := p.Join("u_guest", "d_guest", "Guest", nil, "")
	if err != nil {
		t.Fatalf("guest join: %v", err)
	}
	oldToken := guest.Token
	if p.Remove(guest.MemberId) == nil {
		t.Fatalf("remove returned no member")
	}

	back, err := p.Join("u_guest", "d_guest", "Guest", nil, oldToken)
	if err != nil {
		t.Fatalf("a departed device must be able to join again: %v", err)
	}
	if back.MemberId == guest.MemberId {
		t.Errorf("the removed membership was reused")
	}
	if back.Token == oldToken {
		t.Errorf("the old token was reissued after the membership was removed")
	}
	if back.IsHost {
		t.Errorf("a rejoining guest became host")
	}
	if !host.IsHost {
		t.Errorf("host changed while a guest left and returned")
	}
}

// Two devices racing to claim the same membership: the one with the credential
// wins, the one without is refused, and the party is untouched either way.
func TestConcurrentRejoinKeepsOneMembershipAndOneHost(t *testing.T) {
	p := newTestParty(t)
	host, err := p.Join("u_host", "d_host", "Host", nil, "")
	if err != nil {
		t.Fatalf("host join: %v", err)
	}

	// Production serialises joins with the party lock, so a rejoin race is a
	// race for the lock, not a race on the map.
	done := make(chan error, 8)
	for i := 0; i < 4; i++ {
		go func() {
			p.Lock()
			_, err := p.Join("u_host", "d_host", "Host", nil, host.Token)
			p.Unlock()
			done <- err
		}()
		go func() {
			p.Lock()
			_, err := p.Join("u_host", "d_host", "Host", nil, "not-the-token")
			p.Unlock()
			done <- err
		}()
	}
	for i := 0; i < 8; i++ {
		<-done
	}

	if len(p.Members) != 1 {
		t.Fatalf("membership count changed: %d", len(p.Members))
	}
	if !p.Host().IsHost || p.Host().MemberId != host.MemberId {
		t.Fatalf("host ownership changed")
	}
	if p.Host().Token != host.Token {
		t.Fatalf("token changed under concurrent rejoin")
	}
}
