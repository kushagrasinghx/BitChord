package clock

import "time"

var (
	epochWallMs   = time.Now().UnixMilli()
	epochMonotonic = time.Now()
)

// NowMs returns the server time in milliseconds since the Unix epoch, advancing monotonically
// from startup to ensure NTP steps cannot step the clock backwards under live listening parties.
func NowMs() int64 {
	elapsed := time.Since(epochMonotonic).Milliseconds()
	return epochWallMs + elapsed
}
