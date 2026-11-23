// Web Audio Synthesizer and Customizable TTS Voice Engine for BitChord Pomodoro

chrome.runtime.onMessage.addListener((msg) => {
  if (msg.action === 'play_chime') {
    playChime(msg.soundType || 'break');

    if (msg.ttsEnabled) {
      // Delay speech slightly to let chime ring out nicely
      setTimeout(() => {
        speakVoiceAnnouncement(msg.soundType || 'break', {
          ttsStyle: msg.ttsStyle || 'chill',
          customBreakMessage: msg.customBreakMessage,
          customWorkMessage: msg.customWorkMessage,
        });
      }, 500);
    }
  } else if (msg.action === 'test_tts') {
    playChime(msg.soundType || 'break');
    setTimeout(() => {
      speakVoiceAnnouncement(msg.soundType || 'break', {
        ttsStyle: msg.ttsStyle || 'chill',
        customBreakMessage: msg.customBreakMessage,
        customWorkMessage: msg.customWorkMessage,
      });
    }, 450);
  }
});

// 1. Crystal-clear Web Audio Chimes
function playChime(type) {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const now = ctx.currentTime;

    if (type === 'break') {
      // Uplifting 4-note chime: C5, E5, G5, C6 (Bright, relaxing)
      const notes = [523.25, 659.25, 783.99, 1046.5];
      notes.forEach((freq, i) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, now + i * 0.12);

        gain.gain.setValueAtTime(0, now + i * 0.12);
        gain.gain.linearRampToValueAtTime(0.25, now + i * 0.12 + 0.03);
        gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.12 + 0.6);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now + i * 0.12);
        osc.stop(now + i * 0.12 + 0.65);
      });
    } else if (type === 'work') {
      // Focus Gong / Focus chime (G4, C5 gentle double chime)
      const notes = [392.0, 523.25];
      notes.forEach((freq, i) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(freq, now + i * 0.15);

        gain.gain.setValueAtTime(0, now + i * 0.15);
        gain.gain.linearRampToValueAtTime(0.3, now + i * 0.15 + 0.04);
        gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.15 + 0.9);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now + i * 0.15);
        osc.stop(now + i * 0.15 + 0.95);
      });
    }
  } catch (err) {
    console.error('Failed to play offscreen chime:', err);
  }
}

// 2. Customizable TTS Voice Announcements
const TTS_PERSONAS = {
  chill: {
    breakText: 'Focus session complete. Stretch, take a breath, and let the music flow.',
    workText: 'Break is over. Let us enter deep focus flow.',
    rate: 0.92,
    pitch: 0.95,
  },
  cyber: {
    breakText: 'Session cycle complete. Cognitive subsystems on standby. Audio stream online.',
    workText: 'Deep focus protocol initiated. Commencing neural synchronization.',
    rate: 1.05,
    pitch: 0.85,
  },
  hype: {
    breakText: 'Focus session crushed! Fantastic streak! Time to recharge your energy!',
    workText: 'Round starts now! Time to lock in and conquer your goals!',
    rate: 1.1,
    pitch: 1.15,
  },
  witty: {
    breakText: 'Hey superstar, stop staring at pixels. Your brain earned this coffee break!',
    workText: 'No more slacking off! Back to work, greatness awaits!',
    rate: 1.0,
    pitch: 1.05,
  },
};

function speakVoiceAnnouncement(type, options = {}) {
  if (!('speechSynthesis' in window)) {
    console.warn('SpeechSynthesis API not available in offscreen context.');
    return;
  }

  try {
    window.speechSynthesis.cancel(); // Stop any pending utterance

    const styleKey = options.ttsStyle || 'chill';
    const persona = TTS_PERSONAS[styleKey] || TTS_PERSONAS.chill;

    let textToSpeak = '';
    if (styleKey === 'custom') {
      textToSpeak = type === 'break'
        ? (options.customBreakMessage || persona.breakText)
        : (options.customWorkMessage || persona.workText);
    } else {
      textToSpeak = type === 'break' ? persona.breakText : persona.workText;
    }

    const utterance = new SpeechSynthesisUtterance(textToSpeak);
    utterance.rate = persona.rate || 1.0;
    utterance.pitch = persona.pitch || 1.0;
    utterance.volume = 1.0;

    // Pick a natural voice if available
    const voices = window.speechSynthesis.getVoices();
    if (voices && voices.length > 0) {
      const preferred = voices.find((v) =>
        v.lang.startsWith('en') &&
        (v.name.includes('Google') || v.name.includes('Natural') || v.name.includes('Samantha') || v.name.includes('Zira') || v.name.includes('David'))
      ) || voices.find((v) => v.lang.startsWith('en')) || voices[0];

      if (preferred) utterance.voice = preferred;
    }

    window.speechSynthesis.speak(utterance);
  } catch (err) {
    console.error('Speech synthesis announcement error:', err);
  }
}
