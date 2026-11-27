/*
 * Grouped inset rows, the phone's settings idiom (app/.../ui/screens/
 * SettingsSheet.kt SettingsGroup, SettingsRow): cards of rows 14px round on
 * surfaceVariant, an 11px uppercase header over each and a plain-language
 * footer under it. Settings and its pages use it, and the Extension page.
 */

const row = 'relative flex min-h-[52px] items-center justify-between gap-3 px-4 py-3 type-body hairline-[16px]';
/*
 * The standard row: a 22px glyph, 14px to the text, at least 52px tall with
 * 12/16 padding; the divider between rows starts under the text, 52px in
 * (ROW_INSET + ICON_SIZE + ICON_GAP).
 */
const glyphRow = 'relative flex min-h-[52px] w-full items-center gap-3.5 px-4 py-3 text-start type-body hairline-[52px]';
const integration = 'relative grid min-h-[52px] items-center gap-x-3 px-4 py-3 type-body hairline-[52px]';
const field = 'w-[220px] flex-none rounded-[8px] border-0 bg-quaternary px-2.5 py-[5px] type-body text-primary';

export const settingsUi = {
  /** The page title and its rows as one column, centred in the page. */
  centered: 'mx-auto max-w-[calc(640px+2*var(--body-gutter))]',
  body: 'mx-(--body-gutter) mt-5 mb-[120px] max-w-[640px]',
  group: 'overflow-hidden rounded-group bg-surface-variant',
  /** labelSmall, uppercase, 4px in from the card's edge and 26px above it. */
  sectionHeader: 'mx-1 mt-[26px] mb-2 type-subhead-emphasized uppercase text-on-surface-variant',
  footer: 'mx-1 mt-2 type-callout text-on-surface-variant',

  row,
  /** A row holding a switch, a select or a field: the whole row is its label. */
  controlRow: `${row} cursor-pointer gap-4`,
  glyphRow,
  /** A glyph row that is itself the control: a link, a button, a switch's label. */
  pressableRow: `${glyphRow} cursor-pointer hover:bg-quinary has-disabled:cursor-default has-disabled:hover:bg-transparent`,
  /** The row's glyph, in the page's text colour (the account's avatar takes its place). */
  glyph: 'size-[22px] flex-none rounded-full object-cover text-primary',
  rowText: 'flex min-w-0 flex-1 flex-col gap-0.5',
  rowDetail: 'type-callout text-on-surface-variant',
  value: 'inline-flex flex-none items-center gap-1 text-right type-body text-on-surface-variant',
  dot: 'size-2 rounded-full bg-green',

  /** Download rows: browser logo, title and detail, chevron. */
  download:
    'relative grid grid-cols-[28px_minmax(0,1fr)_auto] items-center gap-x-4 px-4 py-[11px] hover:bg-quinary hairline-[60px]',
  updateDot: 'size-2.5 justify-self-center rounded-full bg-key',
  downloadText: 'flex min-w-0 flex-col gap-0.5',
  downloadTitle: 'type-title-3-emphasized text-key',
  downloadDetail: 'type-body-reduced text-on-surface-variant',
  /** A row's chevron: 20px, onSurfaceVariant at 70% (SettingsSheet.kt Chevron). */
  chevron: 'size-5 flex-none text-on-surface-variant/70',
  steps: 'mx-4 mt-2.5 list-decimal ps-[18px] type-body-tall text-on-surface-variant [&>li+li]:mt-1',

  /** Integration rows: a coloured app-icon square, the name, the state. */
  integration: `${integration} grid-cols-[24px_minmax(0,1fr)_auto]`,
  /** An integration row without the icon square. */
  integrationPlain: `${integration} grid-cols-[minmax(0,1fr)_auto]`,
  integrationIcon: 'grid size-6 place-items-center rounded-[6px] text-white',
  integrationName: 'min-w-0',
  /** A row's own action, after its status (Connect, Disconnect). */
  inlineButton: 'ml-2 flex-none rounded-pill bg-quaternary px-3 py-1 type-callout-emphasized text-primary disabled:opacity-50',
  inlineLink: 'text-key',

  input: field,
};
