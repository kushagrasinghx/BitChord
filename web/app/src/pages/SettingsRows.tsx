import { ChevronRight } from 'lucide-react';
import { createContext, useContext, type ComponentType, type ReactNode } from 'react';
import { SegmentedControl, type Segment } from '@/components/ui/SegmentedControl';
import { Switch } from '@/components/ui/Switch';
import { settingsUi } from './settingsStyles';

/** Any 22px glyph: lucide's, or a react-icons brand mark drawn in the text colour. */
export type Glyph = ComponentType<{ size?: number; strokeWidth?: number; className?: string; 'aria-hidden'?: boolean }>;

/**
 * What the Settings search field holds. Every row asks it whether it answers
 * (SettingsSheet.kt SearchableSettingsGroup's row(title, keywords…)) and
 * draws nothing if not, so a group of rows that all said no is an empty
 * <section>, which `Group` hides with its header and footer.
 */
const SearchQuery = createContext('');
export const SettingsSearchProvider = SearchQuery.Provider;

/** Whether a row titled [title], also found by [keywords], answers the current search. */
export function useMatches(title: string, keywords: readonly string[] = []): boolean {
  const query = useContext(SearchQuery).trim().toLowerCase();
  return !query || [title, ...keywords].some((text) => text.toLowerCase().includes(query));
}

export function RowGlyph({ icon: Icon }: { icon: Glyph }) {
  // A react-icons brand mark (fill="currentColor") is filled, not stroked: a 2px stroke closes Spotify's thin arcs into a plain disc.
  return <Icon size={22} strokeWidth={2} className={`${settingsUi.glyph} [&[fill=currentColor]]:stroke-0`} aria-hidden />;
}

export function Chevron() {
  return <ChevronRight className={settingsUi.chevron} strokeWidth={2.4} aria-hidden />;
}

/**
 * One card of rows with its uppercase header and footer (SettingsGroup). The
 * rows sit directly in the <section>; one that doesn't answer a search draws
 * nothing, and with none left the whole group goes.
 */
export function Group({ header, footer, children }: { header?: string; footer?: ReactNode; children: ReactNode }) {
  return (
    <div className="mt-[26px] first:mt-0 has-[>section:empty]:hidden">
      {header && <h2 className={`${settingsUi.sectionHeader} mt-0`}>{header}</h2>}
      <section className={settingsUi.group}>{children}</section>
      {footer && <p className={settingsUi.footer}>{footer}</p>}
    </div>
  );
}

/** A row's title and, under it, what it does (bodyLarge over bodyMedium). */
function RowText({ title, detail }: { title: ReactNode; detail?: ReactNode }) {
  return (
    <span className={settingsUi.rowText}>
      <span>{title}</span>
      {detail && <span className={settingsUi.rowDetail}>{detail}</span>}
    </span>
  );
}

/** The standard row (SettingsRow): glyph, title, the line under it, and whatever sits on the right. */
export function SettingsRow({
  icon,
  glyph,
  title,
  detail,
  keywords,
  children,
}: {
  icon: Glyph;
  /** Takes the glyph's place: the account row's avatar. */
  glyph?: ReactNode;
  title: string;
  detail?: ReactNode;
  keywords?: readonly string[];
  children?: ReactNode;
}) {
  if (!useMatches(title, keywords)) return null;
  return (
    <div className={settingsUi.glyphRow}>
      {glyph ?? <RowGlyph icon={icon} />}
      <RowText title={title} detail={detail} />
      {children}
    </div>
  );
}

/** A row that does one thing when pressed (a dialog, an action), with the current value and a chevron. */
export function ButtonRow({
  icon,
  title,
  detail,
  value,
  keywords,
  onClick,
}: {
  icon: Glyph;
  title: string;
  detail?: ReactNode;
  value?: ReactNode;
  keywords?: readonly string[];
  onClick: () => void;
}) {
  if (!useMatches(title, keywords)) return null;
  return (
    <button type="button" className={settingsUi.pressableRow} onClick={onClick}>
      <RowGlyph icon={icon} />
      <RowText title={title} detail={detail} />
      <span className={settingsUi.value}>
        {value}
        <Chevron />
      </span>
    </button>
  );
}

/** A switch row (SettingsRow with an IosSwitch): the whole row is its label. */
export function Toggle({
  icon,
  label,
  detail,
  checked,
  onChange,
  disabled,
  keywords,
}: {
  icon: Glyph;
  label: string;
  detail?: ReactNode;
  checked: boolean;
  onChange: (on: boolean) => void;
  disabled?: boolean;
  keywords?: readonly string[];
}) {
  if (!useMatches(label, keywords)) return null;
  return (
    // Dimmed to 45% when it can't be used, as the phone's rows are.
    <label className={`${settingsUi.pressableRow} has-disabled:opacity-45`}>
      <RowGlyph icon={icon} />
      <RowText title={label} detail={detail} />
      <Switch checked={checked} onChange={onChange} disabled={disabled} />
    </label>
  );
}

/**
 * A setting with a few choices: its title, then the choices in the phone's
 * segmented control under it, as SettingsSheet lays out Theme and Player
 * background, rather than a menu to open.
 */
export function ChoiceRow<T>({
  icon,
  title,
  detail,
  options,
  value,
  onChange,
  keywords,
}: {
  icon: Glyph;
  title: string;
  detail?: ReactNode;
  options: readonly Segment<T>[];
  value: T;
  onChange: (value: T) => void;
  keywords?: readonly string[];
}) {
  if (!useMatches(title, keywords)) return null;
  return (
    <div className={`${settingsUi.glyphRow} flex-wrap`}>
      <RowGlyph icon={icon} />
      <RowText title={title} detail={detail} />
      <SegmentedControl label={title} options={options} value={value} onChange={onChange} className="ms-9 w-[calc(100%-36px)]" />
    </div>
  );
}

/** A text field row: the title, and the field on the right. */
export function FieldRow({ icon, title, children }: { icon: Glyph; title: string; children: ReactNode }) {
  if (!useMatches(title)) return null;
  return (
    <label className={`${settingsUi.glyphRow} cursor-text`}>
      <RowGlyph icon={icon} />
      <span className="min-w-0 flex-1">{title}</span>
      {children}
    </label>
  );
}
