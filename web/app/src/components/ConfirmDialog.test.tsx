// @vitest-environment happy-dom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmDialog, useRememberedConfirm } from './ConfirmDialog';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let host: HTMLDivElement;
let root: Root;

beforeEach(() => {
  localStorage.clear();
  host = document.createElement('div');
  document.body.append(host);
  root = createRoot(host);
});

afterEach(() => {
  act(() => root.unmount());
  host.remove();
});

const dialog = () => document.querySelector('[role=alertdialog]');
const button = (label: string) => [...document.querySelectorAll('button')].find((b) => b.textContent === label)!;
const click = (el: Element) => act(() => (el as HTMLElement).click());

describe('ConfirmDialog', () => {
  it('shows its title and text, and cancels on Escape', () => {
    const onCancel = vi.fn();
    act(() =>
      root.render(
        <ConfirmDialog title="Delete?" confirmLabel="Delete" onConfirm={() => {}} onCancel={onCancel}>
          <p>It can't be undone.</p>
        </ConfirmDialog>,
      ),
    );
    expect(dialog()?.textContent).toContain('Delete?');
    expect(dialog()?.textContent).toContain("It can't be undone.");
    expect(document.querySelector('input[type=checkbox]')).toBeNull();
    act(() => window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it('passes the checkbox to onConfirm', () => {
    const onConfirm = vi.fn();
    act(() =>
      root.render(
        <ConfirmDialog title="T" confirmLabel="Go" dontShowAgain="Never ask" onConfirm={onConfirm} onCancel={() => {}}>
          Body
        </ConfirmDialog>,
      ),
    );
    expect(dialog()?.textContent).toContain('Never ask');
    click(document.querySelector('input[type=checkbox]')!);
    click(button('Go'));
    expect(onConfirm).toHaveBeenCalledWith(true);
  });
});

describe('useRememberedConfirm', () => {
  function Harness({ action, onAsk }: { action: () => void; onAsk: (ask: () => void) => void }) {
    const { ask, dialog } = useRememberedConfirm('test:confirm', action, { title: 'Sure?', confirmLabel: 'Yes', children: 'Body' });
    onAsk(ask);
    return <>{dialog}</>;
  }

  it('asks until dismissed for good, then just runs the action', () => {
    const action = vi.fn();
    let ask = () => {};
    act(() => root.render(<Harness action={action} onAsk={(a) => (ask = a)} />));

    act(() => ask());
    expect(dialog()).not.toBeNull();
    click(button('Cancel'));
    expect(dialog()).toBeNull();
    expect(action).not.toHaveBeenCalled();

    act(() => ask());
    click(button('Yes'));
    expect(action).toHaveBeenCalledOnce();
    expect(localStorage.getItem('test:confirm')).toBeNull();

    act(() => ask());
    click(document.querySelector('input[type=checkbox]')!);
    click(button('Yes'));
    expect(action).toHaveBeenCalledTimes(2);
    expect(localStorage.getItem('test:confirm')).toBe('dismissed');

    act(() => ask());
    expect(dialog()).toBeNull();
    expect(action).toHaveBeenCalledTimes(3);
  });
});
