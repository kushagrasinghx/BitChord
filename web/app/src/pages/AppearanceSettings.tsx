import { SegmentedControl } from '@/components/ui/SegmentedControl';
import { THEME_MODES, useTheme } from '@/state/theme';
import { settingsUi } from './settingsStyles';

/**
 * Settings' Appearance group (SettingsSheet.kt APPEARANCE): Theme, with its
 * three choices in a segmented control under the row's title, as the phone
 * lays it out.
 */
export function AppearanceSettings() {
  const mode = useTheme((s) => s.mode);
  const setMode = useTheme((s) => s.setMode);
  return (
    <>
      <h2 className={settingsUi.sectionHeader}>Appearance</h2>
      <section className={settingsUi.group}>
        <div className="flex flex-col gap-2.5 px-4 pt-[11px] pb-3.5 type-title-3">
          <span>Theme</span>
          <SegmentedControl label="Theme" options={THEME_MODES} value={mode} onChange={setMode} />
        </div>
      </section>
    </>
  );
}
