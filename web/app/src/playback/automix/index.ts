import { AutoDJ, type AutomixHost, type AutoDJSnapshot } from './engine/autodj';
import { Deck } from './engine/deck';
import { LoopDeck } from './engine/loopDeck';
import type { TransitionPlan } from './engine/transition-scheduler';

export type { AutomixHost, AutoDJSnapshot, TransitionPlan };
export type { MixWindow } from './engine/autodj';
export type { LoopMixPlan, SungSpans } from './engine/loopMix';
export { sungSpansFromLines } from './engine/loopMix';
export { analyzer } from './analysis/analyzer';

export { AUTOMIX_BARS } from './choices';

export interface AutomixGraph {
  ctx: AudioContext;
  /** The user's volume and mute. */
  master: GainNode;
  decks: Deck[];
  dj: AutoDJ;
}

/**
 * Routes the player's two <audio> elements through Web Audio, one AutoMix
 * deck each, and builds the mix loop over them. A media element can be
 * routed only once, so this is for the life of the page; from then on the
 * elements stay at volume 1 and the graph carries volume and crossfades.
 */
export function createAutomixGraph(
  audios: HTMLAudioElement[],
  host: (decks: Deck[]) => Omit<AutomixHost, 'ctx' | 'loopDecks'>,
  onUpdate?: (snap: AutoDJSnapshot) => void,
): AutomixGraph {
  const ctx = new AudioContext({ latencyHint: 'playback' });
  void ctx.resume();
  const master = new GainNode(ctx, { gain: 1 });
  master.connect(ctx.destination);
  const decks = audios.map((audio, n) => new Deck(n === 0 ? 'A' : 'B', ctx, audio, master));
  // the loop mix's stretch decks (AutoMix's own), built the first time a loop mix is prepared
  let loops: Promise<[LoopDeck, LoopDeck]> | null = null;
  const loopDecks = () => (loops ??= Promise.all([LoopDeck.create(ctx, master), LoopDeck.create(ctx, master)]));
  const dj = new AutoDJ({ ctx, loopDecks, ...host(decks) }, onUpdate);
  return { ctx, master, decks, dj };
}
