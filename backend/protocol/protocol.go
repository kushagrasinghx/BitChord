package protocol

import (
	"errors"
	"strings"
)

// Server -> Client frame types
const (
	FrameWelcome = "welcome"
	FrameState   = "state"
	FrameQueue   = "queue"
	FrameMembers = "members"
	FramePong    = "pong"
	FrameError   = "error"
	FrameBye     = "bye"
)

// Client -> Server frame types
const (
	FramePing      = "ping"
	FrameControl   = "control"
	FrameSync      = "sync"
	FrameSyncQueue = "syncQueue"
	FrameReport    = "report"
)

// Control actions
const (
	ActionPlay        = "play"
	ActionPause       = "pause"
	ActionSeek        = "seek"
	ActionSetTrack    = "setTrack"
	ActionSetQueue    = "setQueue"
	ActionQueueAdd    = "queueAdd"
	ActionQueueRemove = "queueRemove"
	ActionQueueClear  = "queueClear"
	ActionQueueMove   = "queueMove"
	ActionNext        = "next"
	ActionPrevious    = "previous"
)

// JoinRequest is the identity submitted when creating or joining a party.
type JoinRequest struct {
	UserId      string  `json:"userId"`
	DeviceId    string  `json:"deviceId"`
	DisplayName string  `json:"displayName"`
	AvatarUrl   *string `json:"avatarUrl,omitempty"`
}

// Validate ensures all required identity fields are present and safe.
func (r *JoinRequest) Validate() error {
	r.UserId = strings.TrimSpace(r.UserId)
	if r.UserId == "" || len(r.UserId) > 128 {
		return errors.New("userId must be between 1 and 128 characters")
	}

	r.DeviceId = strings.TrimSpace(r.DeviceId)
	if r.DeviceId == "" || len(r.DeviceId) > 128 {
		return errors.New("deviceId must be between 1 and 128 characters")
	}

	r.DisplayName = strings.TrimSpace(r.DisplayName)
	if r.DisplayName == "" || len(r.DisplayName) > 80 {
		return errors.New("displayName must be between 1 and 80 characters")
	}

	if r.AvatarUrl != nil {
		trimmed := strings.TrimSpace(*r.AvatarUrl)
		if trimmed == "" {
			r.AvatarUrl = nil
		} else if len(trimmed) > 1000 {
			return errors.New("avatarUrl must not exceed 1000 characters")
		} else if !strings.HasPrefix(trimmed, "http://") && !strings.HasPrefix(trimmed, "https://") {
			return errors.New("avatarUrl must be an http(s) URL")
		} else {
			r.AvatarUrl = &trimmed
		}
	}
	return nil
}
